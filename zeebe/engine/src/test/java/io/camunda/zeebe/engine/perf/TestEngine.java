/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.perf;

import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.search.NoopSearchClientsProxy;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.util.ProcessingExporterTransistor;
import io.camunda.zeebe.engine.util.StreamProcessingComposite;
import io.camunda.zeebe.engine.util.TestInterPartitionCommandSender;
import io.camunda.zeebe.engine.util.TestStreams;
import io.camunda.zeebe.engine.util.client.DeploymentClient;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.stream.impl.StreamProcessorBuilder;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.rules.TemporaryFolder;

/** Helper class which should help to make it easy to create an engine for tests. */
public final class TestEngine {

  private final StreamProcessingComposite streamProcessingComposite;
  private final TestStreams testStreams;
  private final int partitionCount;

  private TestEngine(
      final int partitionId,
      final int partitionCount,
      final TestContext testContext,
      final Consumer<StreamProcessorBuilder> processorConfiguration) {
    this.partitionCount = partitionCount;

    testStreams =
        new TestStreams(
            testContext.temporaryFolder(),
            testContext.autoCloseableRule(),
            testContext.actorScheduler(),
            InstantSource.system());
    testStreams.withStreamProcessorMode(StreamProcessorMode.PROCESSING);
    // for performance reasons we want to enable batch processing
    testStreams.maxCommandsInBatch(100);

    testContext
        .autoCloseableRule()
        .manage(
            testStreams.createLogStream(
                StreamProcessingComposite.getLogName(partitionId), partitionId));

    streamProcessingComposite =
        new StreamProcessingComposite(
            testStreams,
            partitionId,
            DefaultZeebeDbFactory.defaultFactory(),
            testContext.actorScheduler());

    final var interPartitionCommandSenders = new ArrayList<TestInterPartitionCommandSender>();
    final var featureFlags = FeatureFlags.createDefaultForTests();

    final var interPartitionCommandSender =
        new TestInterPartitionCommandSender(streamProcessingComposite::newLogStreamWriter);
    interPartitionCommandSenders.add(interPartitionCommandSender);
    testContext
        .autoCloseableRule()
        .manage(
            streamProcessingComposite.startTypedStreamProcessor(
                partitionId,
                (recordProcessorContext) ->
                    EngineProcessors.createEngineProcessors(
                            recordProcessorContext,
                            partitionCount,
                            new SubscriptionCommandSender(partitionId, interPartitionCommandSender),
                            interPartitionCommandSender,
                            featureFlags,
                            JobStreamer.noop(),
                            new NoopSearchClientsProxy())
                        .withListener(
                            new ProcessingExporterTransistor(
                                testStreams.getLogStream(
                                    StreamProcessingComposite.getLogName(partitionId)))),
                Optional.empty(),
                processorConfiguration,
                true));
    interPartitionCommandSenders.forEach(s -> s.initializeWriters(partitionCount));
  }

  public DeploymentClient createDeploymentClient() {
    return new DeploymentClient(streamProcessingComposite, (p) -> p.accept(1), partitionCount);
  }

  public ProcessInstanceClient createProcessInstanceClient() {
    return new ProcessInstanceClient(streamProcessingComposite);
  }

  public static TestEngine createSinglePartitionEngine(final TestContext testContext) {
    return new TestEngine(1, 1, testContext, cfg -> {});
  }

  public void reset() {
    RecordingExporter.reset();
    testStreams.resetLog();
  }

  /**
   * Containing infrastructure related dependencies which might be shared between TestEngines.
   *
   * @param actorScheduler the scheduler which is used during tests
   * @param temporaryFolder the temporary folder where the log and runtime is written to
   * @param autoCloseableRule a collector of all to managed resources, which should be cleaned up
   *     later
   */
  public record TestContext(
      ActorScheduler actorScheduler,
      TemporaryFolder temporaryFolder,
      AutoCloseableRule autoCloseableRule) {}
}
