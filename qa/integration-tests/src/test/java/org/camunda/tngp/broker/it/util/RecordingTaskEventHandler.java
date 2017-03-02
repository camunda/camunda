/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.it.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.event.TaskEvent;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.junit.rules.ExternalResource;

public class RecordingTaskEventHandler extends ExternalResource
{
    protected String subscriptionName = "recording-handler";

    protected final List<TaskEvent> taskEvents = Collections.synchronizedList(new ArrayList<>());

    protected final ClientRule clientRule;
    protected final int topicId;

    protected TopicSubscription subscription;

    public RecordingTaskEventHandler(ClientRule clientRule, int topicId)
    {
        this.clientRule = clientRule;
        this.topicId = topicId;
    }

    @Override
    protected void before() throws Throwable
    {
        final TaskTopicClient client = clientRule.getClient().taskTopic(topicId);

        subscription = client.newSubscription()
            .name(subscriptionName)
            .taskEventHandler((metadata, taskEvent) -> taskEvents.add(taskEvent))
            .open();
    }

    @Override
    protected void after()
    {
        subscription.close();
    }

    public boolean hasTaskEvent(Predicate<TaskEvent> matcher)
    {
        return taskEvents.stream().anyMatch(matcher);
    }

    public List<TaskEvent> getTaskEvents(Predicate<TaskEvent> matcher)
    {
        return taskEvents.stream().filter(matcher).collect(Collectors.toList());
    }

    public static Predicate<TaskEvent> eventType(TaskEventType type)
    {
        return task -> task.getEvent().equals(type.name());
    }

    public static Predicate<TaskEvent> retries(int retries)
    {
        return task -> task.getRetries() == retries;
    }

}
