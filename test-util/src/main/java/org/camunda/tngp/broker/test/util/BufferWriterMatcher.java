package org.camunda.tngp.broker.test.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.hamcrest.Matcher;
import org.mockito.ArgumentMatcher;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

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

    protected static class BufferReaderMatch<T extends BufferReader>
    {
        protected Function<T, Object> propertyExtractor;
        protected Object expectedValue;
        protected Matcher<?> expectedValueMatcher;

        boolean matches(T reader)
        {
            final Object actualValue = propertyExtractor.apply(reader);
            if (expectedValue != null)
            {
                return expectedValue.equals(actualValue);
            }
            else
            {
                return expectedValueMatcher.matches(actualValue);
            }
        }
    }
}
