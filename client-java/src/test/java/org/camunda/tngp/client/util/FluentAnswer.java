package org.camunda.tngp.client.util;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class FluentAnswer implements Answer<Object>
{

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable
    {
        final Class<?> returnType = invocation.getMethod().getReturnType();
        final Object mock = invocation.getMock();

        Object answer = null;

        if (returnType.isAssignableFrom(mock.getClass()))
        {
            answer = mock;
        }

        return answer;
    }
}