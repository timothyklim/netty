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
package io.netty5.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.util.UncheckedBooleanSupplier;
import io.netty5.util.internal.UnstableApi;

import static java.util.Objects.requireNonNull;

/**
 * Allocates a new receive buffer whose capacity is probably large enough to read all inbound data and small enough
 * not to waste its space.
 */
public interface RecvBufferAllocator {
    /**
     * Creates a new handle.  The handle provides the actual operations and keeps the internal information which is
     * required for predicting an optimal buffer capacity.
     */
    Handle newHandle();

    @UnstableApi
    interface Handle {
        /**
         * Creates a new receive buffer whose capacity is probably large enough to read all inbound data and small
         * enough not to waste its space.
         */
        Buffer allocate(BufferAllocator alloc);

        /**
         * Similar to {@link #allocate(BufferAllocator)} except that it does not allocate anything but just tells the
         * capacity.
         */
        int guess();

        /**
         * Reset any counters that have accumulated and recommend how many messages/bytes should be read for the next
         * read loop.
         * <p>
         * This may be used by {@link #continueReading(boolean)} to determine if the read operation should complete.
         * </p>
         * This is only ever a hint and may be ignored by the implementation.
         */
        void reset();

        /**
         * Increment the number of messages that have been read for the current read loop.
         * @param numMessages The amount to increment by.
         */
        void incMessagesRead(int numMessages);

        /**
         * Set the bytes that have been read for the last read operation.
         * This may be used to increment the number of bytes that have been read.
         * @param bytes The number of bytes from the previous read operation. This may be negative if an read error
         * occurs. If a negative value is seen it is expected to be return on the next call to
         * {@link #lastBytesRead()}. A negative value will signal a termination condition enforced externally
         * to this class and is not required to be enforced in {@link #continueReading(boolean)}.
         */
        void lastBytesRead(int bytes);

        /**
         * Get the amount of bytes for the previous read operation.
         * @return The amount of bytes for the previous read operation.
         */
        int lastBytesRead();

        /**
         * Set how many bytes the read operation will (or did) attempt to read.
         * @param bytes How many bytes the read operation will (or did) attempt to read.
         */
        void attemptedBytesRead(int bytes);

        /**
         * Get how many bytes the read operation will (or did) attempt to read.
         * @return How many bytes the read operation will (or did) attempt to read.
         */
        int attemptedBytesRead();

        /**
         * Determine if the current read loop should continue.
         * @param autoRead              {@çode true} if autoread is used, {@code false} otherwise.
         * @return                      {@code true} if the read loop should continue reading. {@code false}
         *                              if the read loop is complete.
         */
        boolean continueReading(boolean autoRead);

        /**
         * Same as {@link Handle#continueReading(boolean)} except "more data" is determined by the supplier parameter.
         * @param autoRead              {@çode true} if autoread is used, {@code false} otherwise.
         * @param maybeMoreDataSupplier A supplier that determines if there maybe more data to read.
         * @return                      {@code true} if the read loop should continue reading. {@code false} if the
         * read loop is complete.
         */
        boolean continueReading(boolean autoRead, UncheckedBooleanSupplier maybeMoreDataSupplier);

        /**
         * The read has completed.
         */
        void readComplete();
    }

    /**
     * A {@link Handle} which delegates all call to some other {@link Handle}.
     */
    class DelegatingHandle implements Handle {
        private final Handle delegate;

        public DelegatingHandle(Handle delegate) {
            this.delegate = requireNonNull(delegate, "delegate");
        }

        /**
         * Get the {@link Handle} which all methods will be delegated to.
         * @return the {@link Handle} which all methods will be delegated to.
         */
        protected final Handle delegate() {
            return delegate;
        }

        @Override
        public Buffer allocate(BufferAllocator alloc) {
            return delegate.allocate(alloc);
        }

        @Override
        public int guess() {
            return delegate.guess();
        }

        @Override
        public void reset() {
            delegate.reset();
        }

        @Override
        public void incMessagesRead(int numMessages) {
            delegate.incMessagesRead(numMessages);
        }

        @Override
        public void lastBytesRead(int bytes) {
            delegate.lastBytesRead(bytes);
        }

        @Override
        public int lastBytesRead() {
            return delegate.lastBytesRead();
        }

        @Override
        public boolean continueReading(boolean autoRead) {
            return delegate.continueReading(autoRead);
        }

        @Override
        public boolean continueReading(boolean autoRead, UncheckedBooleanSupplier maybeMoreDataSupplier) {
            return delegate.continueReading(autoRead, maybeMoreDataSupplier);
        }

        @Override
        public int attemptedBytesRead() {
            return delegate.attemptedBytesRead();
        }

        @Override
        public void attemptedBytesRead(int bytes) {
            delegate.attemptedBytesRead(bytes);
        }

        @Override
        public void readComplete() {
            delegate.readComplete();
        }
    }
}
