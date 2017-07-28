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
package io.zeebe.perftest;

import static io.zeebe.perftest.CommonProperties.DEFAULT_TOPIC_NAME;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import io.zeebe.client.TasksClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.perftest.helper.FixedRateLatencyTest;


public class CreateTaskLatencyTest extends FixedRateLatencyTest
{
    private static final String TASK_TYPE = "some-task-type";

    @Override
    protected void setDefaultProperties(Properties properties)
    {
        properties.putIfAbsent(TEST_REQUESTRATE, "50000");

        super.setDefaultProperties(properties);
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(ZeebeClient client)
    {
        final TasksClient taskClient = client.tasks();

        return () ->
        {
            return taskClient.create(DEFAULT_TOPIC_NAME, TASK_TYPE).executeAsync();
        };
    }

    public static void main(String[] args)
    {
        new CreateTaskLatencyTest().run();
    }
}
