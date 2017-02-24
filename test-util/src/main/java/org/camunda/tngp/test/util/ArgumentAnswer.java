package org.camunda.tngp.test.util;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Identity function on one of the arguments
 *
 * @author Lindhauer
 */
public class ArgumentAnswer<T> implements Answer<T>
{

    protected int argIndex;

    public ArgumentAnswer(int argIndex)
    {
        this.argIndex = argIndex;
    }

    @Override
    public T answer(InvocationOnMock invocation) throws Throwable
    {
        return (T) invocation.getArguments()[argIndex];
    }
}
