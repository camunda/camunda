/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter.stream;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.util.TestStreams;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.List;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ExporterRule implements TestRule {

  private static final int EXPORTER_PROCESSOR_ID = 101;
  private static final String PROCESSOR_NAME = "exporter";
  private static final String STREAM_NAME = "stream";

  // environment
  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);
  private final RuleChain chain;

  private final ZeebeDbFactory zeebeDbFactory;
  private ZeebeDb<ZbColumnFamilies> capturedZeebeDb;

  private TestStreams streams;
  private ExporterDirector director;

  public ExporterRule(final int partitionId) {
    this(partitionId, DefaultZeebeDbFactory.defaultFactory(ZbColumnFamilies.class));
  }

  public ExporterRule(final int partitionId, final ZeebeDbFactory dbFactory) {
    final SetupRule rule = new SetupRule(partitionId);

    zeebeDbFactory = dbFactory;
    chain =
        RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables).around(rule);
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    return chain.apply(base, description);
  }

  @SuppressWarnings("unchecked")
  public void startExporterDirector(final List<ExporterDescriptor> exporterDescriptors) {
    final var stream = streams.getLogStream(STREAM_NAME);

    final var snapshotStorage = streams.createSnapshotStorage(stream);
    final var snapshotController =
        spy(new StateSnapshotController(zeebeDbFactory, snapshotStorage));
    capturedZeebeDb = spy(snapshotController.openDb());

    doAnswer(invocationOnMock -> capturedZeebeDb).when(snapshotController).openDb();

    final ExporterDirectorContext context =
        new ExporterDirectorContext()
            .id(EXPORTER_PROCESSOR_ID)
            .name(PROCESSOR_NAME)
            .logStream(stream.getAsyncLogStream())
            .zeebeDb(capturedZeebeDb)
            .descriptors(exporterDescriptors);

    director = new ExporterDirector(context);
    director.startAsync(actorSchedulerRule.get()).join();
    closeables.manage(snapshotStorage);
  }

  public ControlledActorClock getClock() {
    return clock;
  }

  public ExportersState getExportersState() {
    if (capturedZeebeDb == null) {
      throw new IllegalStateException(
          "Exporter director has to be started before accessing the database.");
    }
    return new ExportersState(capturedZeebeDb, capturedZeebeDb.createContext());
  }

  public long writeEvent(final Intent intent, final UnpackedObject value) {
    return writeRecord(RecordType.EVENT, intent, value);
  }

  public long writeCommand(final Intent intent, final UnpackedObject value) {
    return writeRecord(RecordType.COMMAND, intent, value);
  }

  public long writeRecord(
      final RecordType recordType, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(STREAM_NAME)
        .recordType(recordType)
        .intent(intent)
        .event(value)
        .write();
  }

  public void closeExporterDirector() throws Exception {
    director.stopAsync().join();
    capturedZeebeDb.close();
    capturedZeebeDb = null;
  }

  private class SetupRule extends ExternalResource {

    private final int partitionId;

    SetupRule(final int partitionId) {
      this.partitionId = partitionId;
    }

    @Override
    protected void before() {
      streams = new TestStreams(tempFolder, closeables, actorSchedulerRule.get());
      streams.createLogStream(STREAM_NAME, partitionId);
    }
  }
}
