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
package io.zeebe.broker.it;

import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.client.api.commands.Topology;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;

public class GrpcClientRule extends ExternalResource {

  private final Consumer<ZeebeClientBuilder> configurator;

  protected ZeebeClient client;

  public GrpcClientRule(final EmbeddedBrokerRule brokerRule) {
    this(brokerRule, config -> {});
  }

  public GrpcClientRule(
      final EmbeddedBrokerRule brokerRule, final Consumer<ZeebeClientBuilder> configurator) {
    this(
        config -> {
          config.brokerContactPoint(brokerRule.getGatewayAddress().toString());
          configurator.accept(config);
        });
  }

  public GrpcClientRule(final ClusteringRule clusteringRule) {
    this(config -> config.brokerContactPoint(clusteringRule.getGatewayAddress().toString()));
  }

  private GrpcClientRule(final Consumer<ZeebeClientBuilder> configurator) {
    this.configurator = configurator;
  }

  @Override
  protected void before() {
    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder();
    configurator.accept(builder);
    client = builder.build();
  }

  @Override
  protected void after() {
    client.close();
    client = null;
  }

  public ZeebeClient getClient() {
    return client;
  }

  public void waitUntilDeploymentIsDone(final long key) {
    waitUntil(
        () ->
            RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
                .withRecordKey(key)
                .exists());
  }

  public List<Integer> getPartitions() {
    final Topology topology = client.newTopologyRequest().send().join();

    return topology.getBrokers().stream()
        .flatMap(i -> i.getPartitions().stream())
        .filter(PartitionInfo::isLeader)
        .map(PartitionInfo::getPartitionId)
        .collect(Collectors.toList());
  }

  public long createSingleJob(String type) {
    return createSingleJob(type, b -> {}, "{}");
  }

  public long createSingleJob(String type, Consumer<ServiceTaskBuilder> consumer) {
    return createSingleJob(type, consumer, "{}");
  }

  public long createSingleJob(
      String type, Consumer<ServiceTaskBuilder> consumer, String variables) {
    final BpmnModelInstance modelInstance = createSingleJobModelInstance(type, consumer);
    final long workflowKey = deployWorkflow(modelInstance);
    final long workflowInstanceKey = createWorkflowInstance(workflowKey, variables);

    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .filter(j -> j.getValue().getHeaders().getWorkflowInstanceKey() == workflowInstanceKey)
        .withType(type)
        .getFirst()
        .getKey();
  }

  public BpmnModelInstance createSingleJobModelInstance(
      String jobType, Consumer<ServiceTaskBuilder> taskBuilderConsumer) {
    return Bpmn.createExecutableProcess("process")
        .startEvent("start")
        .serviceTask(
            "task",
            t -> {
              t.zeebeTaskType(jobType);
              taskBuilderConsumer.accept(t);
            })
        .endEvent("end")
        .done();
  }

  public long deployWorkflow(BpmnModelInstance modelInstance) {
    final DeploymentEvent deploymentEvent =
        getClient()
            .newDeployCommand()
            .addWorkflowModel(modelInstance, "workflow.bpmn")
            .send()
            .join();
    waitUntilDeploymentIsDone(deploymentEvent.getKey());
    return deploymentEvent.getWorkflows().get(0).getWorkflowKey();
  }

  public long createWorkflowInstance(long workflowKey, String variables) {
    return getClient()
        .newCreateInstanceCommand()
        .workflowKey(workflowKey)
        .variables(variables)
        .send()
        .join()
        .getWorkflowInstanceKey();
  }

  public long createWorkflowInstance(long workflowKey) {
    return getClient()
        .newCreateInstanceCommand()
        .workflowKey(workflowKey)
        .send()
        .join()
        .getWorkflowInstanceKey();
  }
}
