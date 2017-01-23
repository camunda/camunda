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

    public static void ensureGreaterThanOrEqual(String property, long testValue, long comparisonValue)
    {
        if (testValue < comparisonValue)
        {
            throw new RuntimeException(property + " must be greater than or equal to " + comparisonValue);
        }

    }

    public static void ensureLessThan(String property, long testValue, long comparisonValue)
    {
        if (testValue >= comparisonValue)
        {
            throw new RuntimeException(property + " must be less than " + comparisonValue);
        }

    }

    public static void ensureLessThanOrEqual(String property, long testValue, long comparisonValue)
    {
        if (testValue > comparisonValue)
        {
            throw new RuntimeException(property + " must be less than or equal to " + comparisonValue);
        }

    }

    public static void ensureNotNullOrEmpty(String property, String testValue)
    {
        ensureNotNull(property, testValue);

        if (testValue.isEmpty())
        {
            throw new RuntimeException(property + " must not be empty");
        }
    }

}
