package org.camunda.tngp.taskqueue.client.cmd;

import java.util.concurrent.TimeUnit;

public interface AsyncResult<R>
{

    /**
     * @return the result.
     * @throws IllegalStateException if the result is not available.
     */
    R get();

    /**
     * Checks whether the result is available in a non-blocking way returning true if the result is available
     * and false if it is not. If this method returns true, it is safe to call {@link #get()}.
     *
     * @return true if the result is available, false otherwise.
     * @throws RuntimeException if case the result timed out.
     */
    boolean poll();

    /**
     * Wait for the result to become available in a blocking way. Returns the result after it is available.
     * Throws an exception if the result times out.
     * @return the result or throws a {@link RuntimeException} in case the result timed out.
     */
    R await();

    /**
     * Wait for the result to become available in a blocking way. Returns the result after it is available.
     * Throws an exception if the result times out.
     * @return the result or throws a {@link RuntimeException} in case the result timed out.
     */
    R await(long timeout, TimeUnit timeUnit);

}
