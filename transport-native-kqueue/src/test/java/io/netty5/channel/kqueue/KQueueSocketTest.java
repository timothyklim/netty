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
package io.netty5.channel.kqueue;

import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.channel.unix.PeerCredentials;
import io.netty5.channel.unix.tests.SocketTest;
import io.netty5.channel.unix.tests.UnixTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KQueueSocketTest extends SocketTest<BsdSocket> {
    @BeforeAll
    public static void loadJNI() {
        KQueue.ensureAvailability();
    }

    @Test
    public void testPeerCreds() throws IOException {
        BsdSocket s1 = BsdSocket.newSocketDomain();
        BsdSocket s2 = BsdSocket.newSocketDomain();

        try {
            DomainSocketAddress dsa = KQueueSocketTestPermutation.newDomainSocketAddress();
            s1.bind(dsa);
            s1.listen(1);

            assertTrue(s2.connect(dsa));
            byte [] addr = new byte[64];
            s1.accept(addr);
            PeerCredentials pc = s1.getPeerCredentials();
            assertNotEquals(pc.uid(), -1);
        } finally {
            s1.close();
            s2.close();
        }
    }

    @Test
    public void testPeerPID() throws IOException {
        BsdSocket s1 = BsdSocket.newSocketDomain();
        BsdSocket s2 = BsdSocket.newSocketDomain();

        try {
            DomainSocketAddress dsa = KQueueSocketTestPermutation.newDomainSocketAddress();
            s1.bind(dsa);
            s1.listen(1);

            // PID of client socket is expected to be 0 before connection
            assertEquals(0, s2.getPeerCredentials().pid());
            assertTrue(s2.connect(dsa));
            byte [] addr = new byte[64];
            int clientFd = s1.accept(addr);
            assertNotEquals(-1, clientFd);
            PeerCredentials pc = new BsdSocket(clientFd).getPeerCredentials();
            assertNotEquals(0, pc.pid());
            assertNotEquals(0, s2.getPeerCredentials().pid());
            // Server socket FDs should not have pid field set:
            assertEquals(0, s1.getPeerCredentials().pid());
        } finally {
            s1.close();
            s2.close();
        }
    }

    @Override
    protected BsdSocket newSocket() {
        return BsdSocket.newSocketStream();
    }

    @Override
    protected int level() {
        // Value for SOL_SOCKET
        // See https://opensource.apple.com/source/xnu/xnu-201/bsd/sys/socket.h.auto.html
        return 0xffff;
    }

    @Override
    protected int optname() {
        // Value for SO_REUSEADDR
        // See https://opensource.apple.com/source/xnu/xnu-201/bsd/sys/socket.h.auto.html
        return 0x0004;
    }
}
