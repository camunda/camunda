package org.camunda.tngp.test.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.hamcrest.Matcher;
import org.mockito.ArgumentMatcher;

import org.agrona.concurrent.UnsafeBuffer;

/**
 * Note: this matcher does not work when a {@link BufferWriter} is reused throughout a test.
 * Mockito only captures the reference, so after the test the {@link BufferWriter} contains the latest state.
 *
 * @author Lindhauer
 */
public class BufferWriterMatcher<T extends BufferReader> extends ArgumentMatcher<BufferWriter>
{
    protected T reader;

    protected List<BufferReaderMatch<T>> propertyMatchers = new ArrayList<>();

    public BufferWriterMatcher(T reader)
    {
        this.reader = reader;
    }

    @Override
    public boolean matches(Object argument)
    {
        if (argument == null || !(argument instanceof BufferWriter))
        {
            return false;
        }

        final BufferWriter writer = (BufferWriter) argument;

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[writer.getLength()]);
        writer.write(buffer, 0);

        reader.wrap(buffer, 0, buffer.capacity());

        for (BufferReaderMatch<T> matcher : propertyMatchers)
        {
            if (!matcher.matches(reader))
            {
                return false;
            }
        }


        return true;
    }

    public BufferWriterMatcher<T> matching(Function<T, Object> actualProperty, Object expectedValue)
    {
        final BufferReaderMatch<T> match = new BufferReaderMatch<>();
        match.propertyExtractor = actualProperty;

        if (expectedValue instanceof Matcher)
        {
            match.expectedValueMatcher = (Matcher<?>) expectedValue;
        }
        else
        {
            match.expectedValue = expectedValue;
        }

        propertyMatchers.add(match);

        return this;
    }

    public static <T extends BufferReader> BufferWriterMatcher<T> writesProperties(Class<T> readerClass)
    {
        try
        {
            final BufferWriterMatcher<T> matcher = new BufferWriterMatcher<>(readerClass.newInstance());


            return matcher;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not construct matcher", e);
        }
    }
}
