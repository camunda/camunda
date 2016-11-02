package org.camunda.tngp.logstreams.spi;

import java.io.InputStream;

/**
 * Represents a snapshot of the log.
 */
public interface ReadableSnapshot
{
    /**
     * The log position at which the snapshot was taken.
     */
    long getPosition();

    /**
     * Input stream to read the snapshot data.
     *
     * @return the snapshot data as input stream
     */
    InputStream getData();

    /**
     * Consumers of this API must call this method after having read the input
     * stream. The method validates that the bytes read are valid and closes any
     * underlying resources.
     *
     * @throws Exception
     *             if not valid
     */
    void validateAndClose() throws Exception;

}
