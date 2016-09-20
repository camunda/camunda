package org.camunda.tngp.broker.test.util;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class FluentAnswer implements Answer<Object>
{

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable
    {
        final Class<?> returnType = invocation.getMethod().getReturnType();

        Object answer = null;

        if (returnType == Object.class)
        {
            // workaround for methods with a generic return type without an upper bound.
            // Such types are erased to Object at runtime and we don't want to mock such methods
            return answer;
        }

        final Object mock = invocation.getMock();


        if (returnType.isAssignableFrom(mock.getClass()))
        {
            answer = mock;
        }

        return answer;
    }
}