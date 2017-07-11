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
package io.zeebe.client.task.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.client.task.impl.subscription.EventSubscriptions;
import io.zeebe.client.task.impl.subscription.SubscriptionExecutor;
import io.zeebe.client.task.impl.subscription.TaskSubscriptionImpl;
import org.junit.Test;

public class TaskExecutorTest
{
    @Test
    public void shouldExecuteTasks() throws Exception
    {
        // given
        final EventSubscriptions<TaskSubscriptionImpl> subscriptions = new EventSubscriptions<>();

        final TaskSubscriptionImpl subscription = mock(TaskSubscriptionImpl.class);
        when(subscription.isManagedSubscription()).thenReturn(true);
        when(subscription.poll()).thenReturn(34);
        subscriptions.addSubscription(subscription);

        final SubscriptionExecutor executor = new SubscriptionExecutor(subscriptions);

        // when
        final int workCount = executor.doWork();

        // then
        assertThat(workCount).isEqualTo(34);

        verify(subscription).poll();
    }
}
