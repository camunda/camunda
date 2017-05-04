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
package org.camunda.tngp.broker.it.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.it.util.RecordingTaskEventHandler.eventType;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.util.TestUtil.waitUntil;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.RecordingTaskEventHandler;
import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.TopicEvent;
import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicEventType;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.client.impl.cmd.taskqueue.TaskEventType;
import org.camunda.tngp.client.incident.IncidentResolveResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IncidentTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public RecordingTaskEventHandler taskEventHandler = new RecordingTaskEventHandler(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(taskEventHandler);

    private IncidentEventRecoder incidentEventRecorder;
    private TopicSubscription incidentTopicSubscription;

    @Before
    public void init()
    {
        final BpmnModelInstance modelInstance = wrap(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("failingTask")
                    .done())
                    .taskDefinition("failingTask", "test", 3)
                    .ioMapping("failingTask")
                        .input("$.foo", "$.foo")
                        .done();

        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(modelInstance)
            .execute();

        incidentEventRecorder = new IncidentEventRecoder();

        incidentTopicSubscription = clientRule.topic().newSubscription()
            .name("incident")
            .startAtHeadOfTopic()
            .handler(incidentEventRecorder)
            .open();
    }

    @After
    public void cleanUp()
    {
        incidentTopicSubscription.close();
    }

    @Test
    public void shouldResolveIncident()
    {
        // given
        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> incidentEventRecorder.getIncidentKey() > 0);

        // when
        final IncidentResolveResult result = clientRule.incidentTopic().resolve()
            .incidentKey(incidentEventRecorder.getIncidentKey())
            .modifiedPayload("{\"foo\": \"bar\"}")
            .execute();

        // then
        assertThat(result.isIncidentResolved()).isTrue();

        assertThat(taskEventHandler.hasTaskEvent(eventType(TaskEventType.CREATED)));
    }

    private final class IncidentEventRecoder implements TopicEventHandler
    {
        private long incidentKey = -1;

        @Override
        public void handle(EventMetadata metadata, TopicEvent event) throws Exception
        {
            if (metadata.getEventType() == TopicEventType.INCIDENT)
            {
                incidentKey = metadata.getEventKey();
            }
        }

        public long getIncidentKey()
        {
            return incidentKey;
        }
    }

}
