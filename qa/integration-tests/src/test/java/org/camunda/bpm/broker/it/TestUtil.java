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

        public T until(Function<T, Boolean> condition)
        {
            int numTries = 0;

            T result = null;

            do
            {
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
                    LangUtil.rethrowUnchecked(e);
                }
                numTries++;
            }
            while (numTries < MAX_RETRIES && !condition.apply(result));

            return result;
        }

    }
}
