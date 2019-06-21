/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.util;

import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processor.CopiedRecords;
import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.workflow.EngineProcessors;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processor.workflow.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.processor.workflow.message.command.PartitionCommandSender;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandMessageHandler;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.util.client.DeploymentClient;
import io.zeebe.engine.util.client.IncidentClient;
import io.zeebe.engine.util.client.JobActivationClient;
import io.zeebe.engine.util.client.JobClient;
import io.zeebe.engine.util.client.PublishMessageClient;
import io.zeebe.engine.util.client.VariableClient;
import io.zeebe.engine.util.client.WorkflowInstanceClient;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.CopiedRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class EngineRule extends ExternalResource {

  private static final int PARTITION_ID = Protocol.DEPLOYMENT_PARTITION;
  private static final RecordingExporter RECORDING_EXPORTER = new RecordingExporter();

  private final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final StreamProcessorRule environmentRule;

  private final int partitionCount;

  public EngineRule() {
    this(1);
  }

  public EngineRule(int partitionCount) {
    this.partitionCount = partitionCount;
    environmentRule =
        new StreamProcessorRule(
            PARTITION_ID, partitionCount, DefaultZeebeDbFactory.DEFAULT_DB_FACTORY);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    Statement statement = recordingExporterTestWatcher.apply(base, description);
    statement = super.apply(statement, description);
    return environmentRule.apply(statement, description);
  }

  @Override
  protected void before() {

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    final UnsafeBuffer deploymentBuffer = new UnsafeBuffer(new byte[deploymentRecord.getLength()]);
    deploymentRecord.write(deploymentBuffer, 0);

    final PendingDeploymentDistribution deploymentDistribution =
        mock(PendingDeploymentDistribution.class);
    when(deploymentDistribution.getDeployment()).thenReturn(deploymentBuffer);

    forEachPartition(
        partitonId -> {
          final int currentPartitionId = partitonId;
          environmentRule.startTypedStreamProcessor(
              partitonId,
              (processingContext) ->
                  EngineProcessors.createEngineProcessors(
                          processingContext,
                          partitionCount,
                          new SubscriptionCommandSender(
                              currentPartitionId, new PartitionCommandSenderImpl()),
                          new DeploymentDistributionImpl())
                      .withListener(new ProcessingExporterTransistor()));
        });
  }

  public void forEachPartition(Consumer<Integer> partitionIdConsumer) {
    int partitionId = PARTITION_ID;
    for (int i = 0; i < partitionCount; i++) {
      partitionIdConsumer.accept(partitionId++);
    }
  }

  public void increaseTime(Duration duration) {
    environmentRule.getClock().addTime(duration);
  }

  public List<Integer> getPartitionIds() {
    return IntStream.range(PARTITION_ID, PARTITION_ID + partitionCount)
        .boxed()
        .collect(Collectors.toList());
  }

  public ControlledActorClock getClock() {
    return environmentRule.getClock();
  }

  public DeploymentClient deployment() {
    return new DeploymentClient(environmentRule, this::forEachPartition);
  }

  public WorkflowInstanceClient workflowInstance() {
    return new WorkflowInstanceClient(environmentRule);
  }

  public PublishMessageClient message() {
    return new PublishMessageClient(environmentRule, partitionCount);
  }

  public VariableClient variables() {
    return new VariableClient(environmentRule);
  }

  public JobActivationClient jobs() {
    return new JobActivationClient(environmentRule);
  }

  public JobClient job() {
    return new JobClient(environmentRule);
  }

  public IncidentClient incident() {
    return new IncidentClient(environmentRule);
  }

  public Record<JobRecordValue> createJob(final String type, final String processId) {
    deployment()
        .withXmlResource(
            processId,
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .serviceTask("task", b -> b.zeebeTaskType(type).done())
                .endEvent("end")
                .done())
        .deploy();

    final long instanceKey = workflowInstance().ofBpmnProcessId(processId).create();

    return jobRecords(JobIntent.CREATED)
        .withType(type)
        .filter(r -> r.getValue().getWorkflowInstanceKey() == instanceKey)
        .getFirst();
  }

  private class DeploymentDistributionImpl implements DeploymentDistributor {

    private final Map<Long, PendingDeploymentDistribution> pendingDeployments = new HashMap<>();

    @Override
    public ActorFuture<Void> pushDeployment(long key, long position, DirectBuffer buffer) {
      final PendingDeploymentDistribution pendingDeployment =
          new PendingDeploymentDistribution(buffer, position);
      pendingDeployments.put(key, pendingDeployment);

      forEachPartition(
          partitionId -> {
            if (partitionId == PARTITION_ID) {
              return;
            }

            final DeploymentRecord deploymentRecord = new DeploymentRecord();
            deploymentRecord.wrap(buffer);

            environmentRule.writeCommandOnPartition(
                partitionId, key, DeploymentIntent.CREATE, deploymentRecord);
          });

      return CompletableActorFuture.completed(null);
    }

    @Override
    public PendingDeploymentDistribution removePendingDeployment(long key) {
      return pendingDeployments.remove(key);
    }
  }

  private class PartitionCommandSenderImpl implements PartitionCommandSender {

    private final SubscriptionCommandMessageHandler handler =
        new SubscriptionCommandMessageHandler(Runnable::run, environmentRule::getLogStream);

    @Override
    public boolean sendCommand(int receiverPartitionId, BufferWriter command) {

      final byte[] bytes = new byte[command.getLength()];
      final UnsafeBuffer commandBuffer = new UnsafeBuffer(bytes);
      command.write(commandBuffer, 0);

      handler.apply(bytes);
      return true;
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////// PROCESSOR EXPORTER CROSSOVER ///////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////////////

  private static class ProcessingExporterTransistor implements StreamProcessorLifecycleAware {

    private BufferedLogStreamReader logStreamReader;

    @Override
    public void onOpen(ReadonlyProcessingContext context) {
      final ActorControl actor = context.getActor();

      final ActorCondition onCommitCondition =
          actor.onCondition("on-commit", this::onNewEventCommitted);
      final LogStream logStream = context.getLogStream();
      logStream.registerOnCommitPositionUpdatedCondition(onCommitCondition);

      logStreamReader = new BufferedLogStreamReader(logStream);
    }

    private void onNewEventCommitted() {
      while (logStreamReader.hasNext()) {
        final LoggedEvent rawEvent = logStreamReader.next();

        final CopiedRecord typedRecord = CopiedRecords.createCopiedRecord(rawEvent);

        RECORDING_EXPORTER.export(typedRecord);
      }
    }
  }
}
