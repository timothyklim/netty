/*
 * Copyright 2012 The Netty Project
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
package io.netty5.channel.socket;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelOption;


/**
 * A TCP/IP socket {@link Channel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link Channel},
 * {@link SocketChannel} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_KEEPALIVE}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_REUSEADDR}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_LINGER}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#TCP_NODELAY}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_RCVBUF}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#SO_SNDBUF}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#IP_TOS}</td>
 * </tr>
 * </table>
 */
public interface SocketChannel extends Channel {
    @Override
    ServerSocketChannel parent();
}
