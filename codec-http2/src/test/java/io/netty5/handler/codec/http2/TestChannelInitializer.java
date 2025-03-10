/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.util.UncheckedBooleanSupplier;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Channel initializer useful in tests.
 */
public class TestChannelInitializer extends ChannelInitializer<Channel> {
    ChannelHandler handler;
    AtomicInteger maxReads;

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    public void initChannel(Channel channel) {
        if (handler != null) {
            channel.pipeline().addLast(handler);
            handler = null;
        }
        if (maxReads != null) {
            channel.setOption(ChannelOption.RCVBUFFER_ALLOCATOR, new TestNumReadsRecvBufferAllocator(maxReads));
        }
    }

    /**
     * Designed to read a single byte at a time to control the number of reads done at a fine granularity.
     */
    static final class TestNumReadsRecvBufferAllocator implements RecvBufferAllocator {
        private final AtomicInteger numReads;
        private TestNumReadsRecvBufferAllocator(AtomicInteger numReads) {
            this.numReads = numReads;
        }

        @Override
        public Handle newHandle() {
            return new Handle() {
                private int attemptedBytesRead;
                private int lastBytesRead;
                private int numMessagesRead;

                @Override
                public Buffer allocate(BufferAllocator alloc) {
                    return alloc.allocate(guess());
                }

                @Override
                public int guess() {
                    return 1; // only ever allocate buffers of size 1 to ensure the number of reads is controlled.
                }

                @Override
                public void reset() {
                    numMessagesRead = 0;
                }

                @Override
                public void incMessagesRead(int numMessages) {
                    numMessagesRead += numMessages;
                }

                @Override
                public void lastBytesRead(int bytes) {
                    lastBytesRead = bytes;
                }

                @Override
                public int lastBytesRead() {
                    return lastBytesRead;
                }

                @Override
                public void attemptedBytesRead(int bytes) {
                    attemptedBytesRead = bytes;
                }

                @Override
                public int attemptedBytesRead() {
                    return attemptedBytesRead;
                }

                @Override
                public boolean continueReading(boolean autoRead) {
                    return numMessagesRead < numReads.get();
                }

                @Override
                public boolean continueReading(boolean autoRead, UncheckedBooleanSupplier maybeMoreDataSupplier) {
                    return continueReading(autoRead);
                }

                @Override
                public void readComplete() {
                    // Nothing needs to be done or adjusted after each read cycle is completed.
                }
            };
        }
    }
}
