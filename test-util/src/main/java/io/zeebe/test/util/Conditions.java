package io.zeebe.test.util;

import org.assertj.core.api.Condition;

/**
 * useful assertj conditions
 */
public class Conditions
{

    public static Condition<Object> isLowerThan(int i)
    {
        return new Condition<>(v ->
        {
            return (int) v < i;
        },
        "lower than %s",
        i
        );
    }
}
