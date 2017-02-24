package org.camunda.tngp.test.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.camunda.tngp.util.buffer.BufferReader;
import org.hamcrest.Matcher;
import org.mockito.ArgumentMatcher;

/**
 * Note: This matcher does not behave as expected when the BufferReader is reused; this would
 * require us to clone the buffer reader's state at the time of invocation
 *
 * @author Lindhauer
 *
 * @param <T>
 */
public class BufferReaderMatcher<T extends BufferReader> extends ArgumentMatcher<T>
{
    protected List<BufferReaderMatch<T>> propertyMatchers = new ArrayList<>();

    @Override
    public boolean matches(Object argument)
    {
        if (argument == null || !(argument instanceof BufferReader))
        {
            return false;
        }

        for (BufferReaderMatch<T> matcher : propertyMatchers)
        {
            if (!matcher.matches((T) argument))
            {
                return false;
            }
        }


        return true;
    }

    public BufferReaderMatcher<T> matching(Function<T, Object> actualProperty, Object expectedValue)
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

    public static <T extends BufferReader> BufferReaderMatcher<T> readsProperties()
    {
        return new BufferReaderMatcher<>();
    }

}
