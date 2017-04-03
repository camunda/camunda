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
package org.camunda.tngp.test.broker.protocol.clientapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Predicate;
import java.util.stream.Stream;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.protocol.clientapi.EventType;

public class TestTopicClient
{
    private final ClientApiRule apiRule;
    private final int topicId;

    private boolean isTopicSubscriptionOpen = false;

    public TestTopicClient(ClientApiRule apiRule, int topicId)
    {
        this.apiRule = apiRule;
        this.topicId = topicId;
    }

    public long deploy(final BpmnModelInstance modelInstance)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
                .topicId(topicId)
                .eventType(EventType.DEPLOYMENT_EVENT)
                .command()
                    .put("eventType", "CREATE_DEPLOYMENT")
                    .put("bpmnXml", Bpmn.convertToString(modelInstance))
                .done()
                .sendAndAwait();

        assertThat(response.getEvent().get("eventType")).isEqualTo("DEPLOYMENT_CREATED");

        return response.key();
    }

    public long createWorkflowInstance(String bpmnProcessId)
    {
        final ExecuteCommandResponse response = apiRule.createCmdRequest()
                .topicId(topicId)
                .eventTypeWorkflow()
                .command()
                    .put("eventType", "CREATE_WORKFLOW_INSTANCE")
                    .put("bpmnProcessId", bpmnProcessId)
                .done()
                .sendAndAwait();

        assertThat(response.getEvent().get("eventType")).isEqualTo("WORKFLOW_INSTANCE_CREATED");

        return response.key();
    }

    public void completeTaskOfType(String taskType)
    {
        apiRule.openTaskSubscription(topicId, taskType).await();

        final SubscribedEvent taskEvent = apiRule
                .subscribedEvents()
                .filter(taskEvents("LOCKED"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected task locked event but not found."));

        final ExecuteCommandResponse response = apiRule.createCmdRequest()
            .topicId(topicId)
            .key(taskEvent.longKey())
            .eventTypeTask()
            .command()
                .put("eventType", "COMPLETE")
                .put("type", taskType)
                .put("lockOwner", taskEvent.event().get("lockOwner"))
                .put("headers", taskEvent.event().get("headers"))
                .done()
            .sendAndAwait();

        assertThat(response.getEvent().get("eventType")).isEqualTo("COMPLETED");
    }

    /**
     * @return an infinite stream of received subscribed events; make sure to use short-circuiting operations
     *   to reduce it to a finite stream
     */
    public Stream<SubscribedEvent> receiveEvents(Predicate<SubscribedEvent> filter)
    {
        ensureOpenTopicSubscription();

        return apiRule
                .moveSubscribedEventsStreamToHead()
                .subscribedEvents()
                .filter(filter);
    }

    public SubscribedEvent receiveSingleEvent(Predicate<SubscribedEvent> filter)
    {
        return receiveEvents(filter)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no event received"));
    }

    private void ensureOpenTopicSubscription()
    {
        if (!isTopicSubscriptionOpen)
        {
            final ExecuteCommandResponse response = apiRule.openTopicSubscription(topicId, "test", 0).await();
            assertThat(response.key()).isGreaterThanOrEqualTo(0);

            isTopicSubscriptionOpen = true;
        }
    }

    public static Predicate<SubscribedEvent> eventType(String eventType)
    {
        return e -> e.event().get("eventType").equals(eventType);
    }

    public static Predicate<SubscribedEvent> workflowInstanceEvents()
    {
        return e -> e.eventType() == EventType.WORKFLOW_EVENT;
    }

    public static Predicate<SubscribedEvent> workflowInstanceEvents(String eventType)
    {
        return workflowInstanceEvents().and(eventType(eventType));
    }

    public static Predicate<SubscribedEvent> taskEvents()
    {
        return e -> e.eventType() == EventType.TASK_EVENT;
    }

    public static Predicate<SubscribedEvent> taskEvents(String eventType)
    {
        return taskEvents().and(eventType(eventType));
    }
}
