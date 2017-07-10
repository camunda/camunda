/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.util;

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