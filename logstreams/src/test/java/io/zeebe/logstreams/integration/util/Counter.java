/*
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
package io.zeebe.logstreams.integration.util;

import java.io.Serializable;

public class Counter implements Serializable
{
    private static final long serialVersionUID = 1L;

    private long count = 0;

    public long getCount()
    {
        return count;
    }

    public void increment()
    {
        count += 1;
    }

    public void reset()
    {
        count = 0;
    }

    @Override
    public String toString()
    {
        return "Count: " + count;
    }
}