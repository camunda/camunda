/*
 * Zeebe Broker Core
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
package io.zeebe.broker.exporter.stream;

import static io.zeebe.broker.exporter.ExporterServiceNames.exporterDirectorServiceName;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.broker.util.RecordStream;
import io.zeebe.broker.util.TestStreams;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.List;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class ExporterRule implements TestRule {

  private static final int PARTITION_ID = 0;
  private static final int EXPORTER_PROCESSOR_ID = 101;
  private static final String PROCESSOR_NAME = "exporter";
  private static final String STREAM_NAME = "stream";

  // environment
  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);
  private final ServiceContainerRule serviceContainerRule =
      new ServiceContainerRule(actorSchedulerRule);
  private final RuleChain chain;

  private final ZeebeDbFactory zeebeDbFactory;
  private ZeebeDb<ExporterColumnFamilies> capturedZeebeDb;

  private TestStreams streams;

  public ExporterRule(int partitionId) {
    this(partitionId, DefaultZeebeDbFactory.defaultFactory(ExporterColumnFamilies.class));
  }

  public ExporterRule(int partitionId, ZeebeDbFactory dbFactory) {
    final SetupRule rule = new SetupRule(partitionId);

    zeebeDbFactory = dbFactory;
    chain =
        RuleChain.outerRule(tempFolder)
            .around(actorSchedulerRule)
            .around(serviceContainerRule)
            .around(closeables)
            .around(rule);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return chain.apply(base, description);
  }

  @SuppressWarnings("unchecked")
  public void startExporterDirector(List<ExporterDescriptor> exporterDescriptors) {
    final LogStream stream = streams.getLogStream(STREAM_NAME);

    final StateStorage stateStorage =
        streams.getStateStorageFactory().create(EXPORTER_PROCESSOR_ID, PROCESSOR_NAME);
    final StateSnapshotController snapshotController =
        spy(new StateSnapshotController(zeebeDbFactory, stateStorage));

    doAnswer(
            invocationOnMock -> {
              capturedZeebeDb = (ZeebeDb<ExporterColumnFamilies>) invocationOnMock.callRealMethod();
              return capturedZeebeDb;
            })
        .when(snapshotController)
        .openDb();

    final ExporterDirectorContext context =
        new ExporterDirectorContext()
            .eventFilter((event -> true))
            .id(EXPORTER_PROCESSOR_ID)
            .name(PROCESSOR_NAME)
            .logStream(stream)
            .snapshotController(snapshotController)
            .maxSnapshots(1)
            .descriptors(exporterDescriptors)
            .logStreamReader(new BufferedLogStreamReader())
            .snapshotPeriod(Duration.ofMinutes(5));

    final ExporterDirector director = new ExporterDirector(context);
    serviceContainerRule
        .get()
        .createService(exporterDirectorServiceName(PARTITION_ID), director)
        .dependency(LogStreamServiceNames.logStreamServiceName(STREAM_NAME))
        .dependency(LogStreamServiceNames.logWriteBufferServiceName(STREAM_NAME))
        .dependency(LogStreamServiceNames.logStorageServiceName(STREAM_NAME))
        .dependency(LogStreamServiceNames.logBlockIndexServiceName(STREAM_NAME))
        .install()
        .join();
  }

  public ControlledActorClock getClock() {
    return clock;
  }

  public RecordStream events() {
    return new RecordStream(streams.events(STREAM_NAME));
  }

  public ExportersState getExportersState() {
    if (capturedZeebeDb == null) {
      throw new IllegalStateException(
          "Exporter director has to be started before accessing the database.");
    }
    return new ExportersState(capturedZeebeDb, capturedZeebeDb.createContext());
  }

  public long writeEvent(Intent intent, UnpackedObject value) {
    return streams
        .newRecord(STREAM_NAME)
        .recordType(RecordType.EVENT)
        .intent(intent)
        .event(value)
        .write();
  }

  public void closeExporterDirector() {
    capturedZeebeDb = null;
    serviceContainerRule.get().removeService(exporterDirectorServiceName(PARTITION_ID)).join();
  }

  private class SetupRule extends ExternalResource {

    private final int partitionId;

    SetupRule(int partitionId) {
      this.partitionId = partitionId;
    }

    @Override
    protected void before() {
      streams =
          new TestStreams(
              tempFolder, closeables, serviceContainerRule.get(), actorSchedulerRule.get());
      streams.createLogStream(STREAM_NAME, partitionId);
    }
  }
}
