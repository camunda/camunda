package org.camunda.tngp.util.buffer;

import uk.co.real_logic.agrona.DirectBuffer;

/**
 * Implementations may expose methods for access to properties from
 * the buffer that is read. The reader is a <em>view</em> on the buffer
 * Any concurrent changes to the underlying buffer become immediately visible to the reader.
 *
 * @author Lindhauer
 */
public interface BufferReader
{
    /**
     * Wraps a buffer for read access.
     *
     * @param buffer the buffer to read from
     * @param offset the offset at which to start reading
     * @param length the length of the values to read
     */
    void wrap(final DirectBuffer buffer, int offset, int length);
}
