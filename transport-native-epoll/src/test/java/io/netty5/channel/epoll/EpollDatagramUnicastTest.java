/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.channel.epoll;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.FixedRecvBufferAllocator;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.unix.SegmentedDatagramPacket;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.testsuite.transport.socket.DatagramUnicastInetTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.netty5.buffer.api.DefaultBufferAllocators.offHeapAllocator;
import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class EpollDatagramUnicastTest extends DatagramUnicastInetTest {
    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.datagram(StandardProtocolFamily.INET);
    }

    @Override
    public void testSimpleSendWithConnect(Bootstrap sb, Bootstrap cb) throws Throwable {
        // Run this test with IP_RECVORIGDSTADDR option enabled
        sb.option(EpollChannelOption.IP_RECVORIGDSTADDR, true);
        super.testSimpleSendWithConnect(sb, cb);
        sb.option(EpollChannelOption.IP_RECVORIGDSTADDR, false);
    }

    @Test
    public void testSendSegmentedDatagramPacket(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendSegmentedDatagramPacket);
    }

    public void testSendSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, false, false);
    }

    @Test
    public void testSendSegmentedDatagramPacketComposite(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendSegmentedDatagramPacketComposite);
    }

    public void testSendSegmentedDatagramPacketComposite(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, true, false);
    }

    @Test
    public void testSendAndReceiveSegmentedDatagramPacket(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendAndReceiveSegmentedDatagramPacket);
    }

    public void testSendAndReceiveSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, false, true);
    }

    @Test
    public void testSendAndReceiveSegmentedDatagramPacketComposite(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendAndReceiveSegmentedDatagramPacketComposite);
    }

    public void testSendAndReceiveSegmentedDatagramPacketComposite(Bootstrap sb, Bootstrap cb) throws Throwable {
        testSegmentedDatagramPacket(sb, cb, true, true);
    }

    private void testSegmentedDatagramPacket(Bootstrap sb, Bootstrap cb, boolean composite, boolean gro)
            throws Throwable {
        assumeTrue(EpollDatagramChannel.isSegmentedDatagramPacketSupported());
        Channel sc = null;
        Channel cc = null;

        try {
            cb.handler(new SimpleChannelInboundHandler<Object>() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, Object msgs) {
                    // Nothing will be sent.
                }
            });

            cc = cb.bind(newSocketAddress()).asStage().get();
            if (!(cc instanceof EpollDatagramChannel)) {
                // Only supported for the native epoll transport.
                return;
            }
            final int numBuffers = 16;
            final int segmentSize = 512;
            int bufferCapacity = numBuffers * segmentSize;
            final CountDownLatch latch = new CountDownLatch(numBuffers);
            AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            if (gro) {
                // Enable GRO and also ensure we can read everything with one read as otherwise
                // we will drop things on the floor.
                sb.option(EpollChannelOption.UDP_GRO, true);
                sb.option(ChannelOption.RCVBUFFER_ALLOCATOR, new FixedRecvBufferAllocator(bufferCapacity));
            }
            sc = sb.handler(new SimpleChannelInboundHandler<Object>() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof DatagramPacket) {
                        DatagramPacket packet = (DatagramPacket) msg;
                        int packetSize = packet.content().readableBytes();
                        assertEquals(segmentSize, packetSize, "Unexpected datagram packet size");
                        latch.countDown();
                    } else {
                        fail("Unexpected message of type " + msg.getClass() + ": " + msg);
                    }
                }

                @Override
                public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    do {
                        Throwable throwable = errorRef.get();
                        if (throwable != null) {
                            if (throwable != cause) {
                                throwable.addSuppressed(cause);
                            }
                            break;
                        }
                    } while (!errorRef.compareAndSet(null, cause));
                    super.channelExceptionCaught(ctx, cause);
                }
            }).bind(newSocketAddress()).asStage().get();

            if (gro && !(sc instanceof EpollDatagramChannel)) {
                // Only supported for the native epoll transport.
                return;
            }
            if (sc instanceof EpollDatagramChannel) {
                assertEquals(gro, sc.getOption(EpollChannelOption.UDP_GRO));
            }
            InetSocketAddress addr = sendToAddress((InetSocketAddress) sc.localAddress());
            final Buffer buffer;
            if (composite) {
                Buffer[] components = new Buffer[numBuffers];
                for (int i = 0; i < numBuffers; i++) {
                    components[i] = offHeapAllocator().allocate(segmentSize);
                    components[i].fill((byte) 0);
                    components[i].skipWritableBytes(segmentSize);
                }
                buffer = offHeapAllocator().compose(stream(components).map(Buffer::send).collect(Collectors.toList()));
            } else {
                buffer = offHeapAllocator().allocate(bufferCapacity);
                buffer.fill((byte) 0);
                buffer.skipWritableBytes(bufferCapacity);
            }
            cc.writeAndFlush(new SegmentedDatagramPacket(buffer, segmentSize, addr)).asStage().sync();

            if (!latch.await(10, TimeUnit.SECONDS)) {
                Throwable error = errorRef.get();
                if (error != null) {
                    throw error;
                }
                fail();
            }
        } finally {
            if (cc != null) {
                cc.close().asStage().sync();
            }
            if (sc != null) {
                sc.close().asStage().sync();
            }
        }
    }
}
