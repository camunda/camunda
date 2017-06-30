package io.zeebe.test.util;

import java.util.function.Function;

import io.zeebe.util.buffer.BufferReader;
import org.hamcrest.Matcher;

public class BufferReaderMatch<T extends BufferReader>
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
