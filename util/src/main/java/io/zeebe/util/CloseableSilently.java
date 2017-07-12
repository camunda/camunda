package io.zeebe.util;

/**
 * {@link AutoCloseable} which does not allow to throw exceptions on closing.
 */
public interface CloseableSilently extends AutoCloseable
{
    @Override
    void close();
}
