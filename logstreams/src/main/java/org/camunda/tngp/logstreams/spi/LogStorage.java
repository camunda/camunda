package org.camunda.tngp.logstreams.spi;

import java.nio.ByteBuffer;

/**
 * Log structured storage abstraction
 */
public interface LogStorage
{
    /**
     *  Status code returned by the {@link #readBytes(ByteBuffer, int, int, long) read} operation
     *  in case the provided address is invalid and does not exist.
     */
    long OP_RESULT_INVALID_ADDR = -1L;

    /**
     *  Status code returned by the {@link #readBytes(ByteBuffer, int, int, long) read} operation
     *  in case the provided address does exist but data is not available yet. This indicates that
     *  retrying the operation with the same parameters will eventually return data assuming that more
     *  data will be written to the log.
     */
    long OP_RESULT_NO_DATA = -2L;

    /**
     * Status code returned by the {@link #readBytes(ByteBuffer, int, int, long) read} operation
     * only if underlying storage is block-addressable (in contrast to byte addressable).
     * If the storage is block addressable, consumers of this API can only read complete addressable
     * blocks of data at a time. In order to read a block, the provided read buffer must provide
     * sufficient capacity to read at least one complete block. If sufficient capacity is not available
     * in the read buffer to fit at least a complete block, this status code is returned.
     */
    long OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY = -3L;

    /**
     * Status code returned by the {@link #append(ByteBuffer)} operation
     * in case the provided block is too big to write in the storage.
     */
    long OP_RESULT_BLOCK_SIZE_TOO_BIG = -4L;

    /**
     * Writes a block containing one or multiple log entries in the storage and returns the address at which
     * the block has been written.
     *
     * Storage implementations must guarantee eventually atomicity. When this method returns, either all the bytes must
     * be written or none at all.
     *
     * The caller of this method must guarantee that the provided block contains unfragmented log entries.
     *
     * @param blockBuffer the buffer containing a block of log entries to be written into storage
     * @return the address at which the block has been written or error status code
     */
    long append(ByteBuffer blockBuffer);

    /**
     * Reads bytes into the read buffer starting at addr. Returns an operation result status code
     * which is either
     * <ul>
     * <li>positive long representing the next address at which the next block of data can be read</li>
     * <li>{@link #OP_RESULT_INVALID_ADDR}: in case the provided address does not exist</li>
     * <li>{@link #OP_RESULT_NO_DATA}: in case no data is (yet) available at that address</li>
     * <li>{@link #OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY}: in case the storage is block addressable and the provided
     * buffer does not have sufficient capacity to read a whole block</li>
     * </ul>
     *
     * If this method returns with a positive status code, bytes will be available between the
     * readbuffer's {@link ByteBuffer#position()} and {@link ByteBuffer#limit()}.
     *
     * This method is invoked concurrently by consumer threads of the log.
     *
     * @param readBuffer the buffer to read into
     * @param addr the address in the underlying storage from which bytes should be read
     * @return the next address from which bytes can be read or error status code.
     */
    long read(ByteBuffer readBuffer, long addr);

    /**
     * @return true if the storage is byte addressable (each byte managed in the underlying storage can be uniquely
     * addressed using a long addr. False in case the storage is block addressable.
     */
    boolean isByteAddressable();

    /**
     * Open the storage. Called in the log conductor thread.
     */
    void open();

    /**
     * Close the storage. Called in the log conductor thread.
     */
    void close();

    /**
     * @return <code>true</code>, if the storage is open.
     */
    boolean isOpen();

    /**
     * Returns the address of the first block in the storage or -1 if the storage is currently
     * empty.
     */
    long getFirstBlockAddress();

    /**
     * Flushes all appended blocks to ensure that all blocks are written completely.
     * Note that a storage implementation may do nothing if {@link #append(ByteBuffer)} guarantees that
     * all blocks are written immediately.
     *
     * @throws Exception if fails to flush all blocks
     */
    void flush() throws Exception;

}
