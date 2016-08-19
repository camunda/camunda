package org.camunda.bpm.broker.it;

import java.util.concurrent.Callable;
import java.util.function.Function;

import uk.co.real_logic.agrona.LangUtil;

public class TestUtil
{

    public static final int MAX_RETRIES = 5;

    public static <T> Invocation<T> doRepeatedly(Callable<T> callable)
    {
        return new Invocation<>(callable);
    }

    public static class Invocation<T>
    {
        protected Callable<T> callable;

        public Invocation(Callable<T> callable)
        {
            this.callable = callable;
        }

        public T until(Function<T, Boolean> resultCondition)
        {
            return until(resultCondition, (e) -> false);
        }

        public T until(Function<T, Boolean> resultCondition, Function<Exception, Boolean> exceptionCondition)
        {
            int numTries = 0;

            T result;

            do
            {
                result = null;
                try
                {
                    if (numTries > 0)
                    {
                        Thread.sleep(100L);
                    }

                    result = callable.call();
                }
                catch (Exception e)
                {
                    if (exceptionCondition.apply(e))
                    {
                        LangUtil.rethrowUnchecked(e);
                    }
                }
                numTries++;
            }
            while (numTries < MAX_RETRIES && !resultCondition.apply(result));

            return result;
        }
    }
}
