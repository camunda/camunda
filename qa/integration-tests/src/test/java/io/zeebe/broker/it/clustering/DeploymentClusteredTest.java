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
package io.zeebe.broker.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.DeploymentEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.test.util.AutoCloseableRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class DeploymentClusteredTest
{
    private static final int PARTITION_COUNT = 5;

    private static final WorkflowDefinition WORKFLOW = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .endEvent()
            .done();

    public AutoCloseableRule closeables = new AutoCloseableRule();
//    public Timeout testTimeout = Timeout.seconds(30);
    public ClientRule clientRule = new ClientRule(false);
    public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(closeables)
//                 .around(testTimeout)
                 .around(clientRule)
                 .around(clusteringRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ZeebeClient client;

    @Before
    public void init()
    {
        client = clientRule.getClient();
    }

    @Test
    public void shouldDeployWorkflowAndCreateInstances()
    {
        // given
        final int workCount = 10 * PARTITION_COUNT;
        clusteringRule.createTopic("test", PARTITION_COUNT);

        // when
        client.workflows().deploy("test")
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        // then
        for (int p = 0; p < workCount; p++)
        {
            client.workflows().create("test")
                .bpmnProcessId("process")
                .execute();
        }
    }

    @Test
    public void shouldDeployOnRemainingBrokers()
    {
        // given
        clusteringRule.createTopic("test", PARTITION_COUNT);

        // when
        clusteringRule.stopBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);

        // then
        final DeploymentEvent deploymentEvent = client.workflows()
                                           .deploy("test")
                                           .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                                           .execute();

        assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
        assertThat(deploymentEvent.getErrorMessage()).isEmpty();
    }

}
