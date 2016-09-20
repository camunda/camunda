package org.camunda.tngp.util;

public class EnsureUtil
{

    public static void ensureNotNull(String property, Object o)
    {
        if (o == null)
        {
            throw new RuntimeException(property + " must not be null");
        }
    }

    public static void ensureGreaterThan(String property, long testValue, long comparisonValue)
    {
        if (testValue <= comparisonValue)
        {
            throw new RuntimeException(property + " must be greater than " + comparisonValue);
        }

    }
}
