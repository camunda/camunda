/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.capabilities;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.capabilities.ClusterCapabilitiesRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterCapabilitiesIntent;
import io.camunda.zeebe.protocol.record.value.ClusterCapabilitiesRecordValue;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.ecv.Capability;
import io.camunda.zeebe.util.ecv.CapabilitySet;
import io.camunda.zeebe.util.ecv.EnabledCapabilitiesView;
import io.camunda.zeebe.util.ecv.PartitionCapabilities;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class ClusterCapabilitiesTest {

  private static final CapabilitySet BASE = CapabilitySet.of(Capability.ECV_FRAMEWORK);
  private static final CapabilitySet WITH_FORMAT_CHANGE =
      CapabilitySet.of(Capability.ECV_FRAMEWORK, Capability.POC_812_FORMAT_CHANGE);
  // POC_812_DEPENDENT_BEHAVIOR without its prerequisite POC_812_FORMAT_CHANGE
  private static final CapabilitySet MISSING_PREREQUISITE =
      CapabilitySet.of(Capability.ECV_FRAMEWORK, Capability.POC_812_DEPENDENT_BEHAVIOR);

  private final TestPartitionCapabilities capabilities = new TestPartitionCapabilities();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition().withPartitionCapabilities(capabilities);

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCommitTheViewWhenTheLeaderRecovers() {
    // given
    capabilities.view.set(WITH_FORMAT_CHANGE);

    // when
    restart();

    // then
    assertThat(committed(WITH_FORMAT_CHANGE)).isNotNull();
    assertThat(engine.getProcessingState().getClusterCapabilitiesState().getEnabledCapabilities())
        .isEqualTo(WITH_FORMAT_CHANGE);
    Awaitility.await("the leader reports its progress")
        .untilAsserted(
            () -> assertThat(capabilities.lastProgress().applied()).isEqualTo(WITH_FORMAT_CHANGE));
  }

  @Test
  public void shouldCommitTheViewWhenItGrows() {
    // given
    capabilities.view.set(BASE);
    restart();
    committed(BASE);

    // when
    capabilities.view.set(Capability.all());

    // then
    assertThat(committed(Capability.all())).isNotNull();
  }

  @Test
  public void shouldApplyCommitsAsUnionOnly() {
    // given - the view is empty, so the leader writes nothing itself
    restart();

    // when
    writeCommit(WITH_FORMAT_CHANGE);
    writeCommit(BASE);
    writeCommit(WITH_FORMAT_CHANGE);

    // then - an older or a repeated set does not make the enabled set smaller
    assertThat(
            RecordingExporter.clusterCapabilitiesRecords(ClusterCapabilitiesIntent.COMMITTED)
                .limit(3)
                .map(record -> record.getValue().getEnabledCapabilities())
                .toList())
        .containsExactly(
            WITH_FORMAT_CHANGE.encode(), WITH_FORMAT_CHANGE.encode(), WITH_FORMAT_CHANGE.encode());
    assertThat(engine.getProcessingState().getClusterCapabilitiesState().getEnabledCapabilities())
        .isEqualTo(WITH_FORMAT_CHANGE);
  }

  @Test
  public void shouldRejectSetWithMissingPrerequisitesAndNotWriteItAgain() {
    // given
    capabilities.view.set(MISSING_PREREQUISITE);
    restart();
    final var rejection =
        RecordingExporter.clusterCapabilitiesRecords()
            .onlyCommandRejections()
            .withEnabledCapabilities(MISSING_PREREQUISITE.encode())
            .getFirst();

    // when - the view notifies the same set again, then grows to a valid set
    capabilities.view.set(MISSING_PREREQUISITE);
    capabilities.view.set(Capability.all());

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains("POC_812_DEPENDENT_BEHAVIOR requires POC_812_FORMAT_CHANGE");
    committed(Capability.all());
    assertThat(
            RecordingExporter.clusterCapabilitiesRecords(ClusterCapabilitiesIntent.COMMIT)
                .onlyCommands()
                .limit(r -> r.getValue().getEnabledCapabilities().equals(Capability.all().encode()))
                .map(r -> r.getValue().getEnabledCapabilities())
                .toList())
        .containsExactly(MISSING_PREREQUISITE.encode(), Capability.all().encode());
    final var progress = capabilities.lastProgress();
    assertThat(progress.applied()).isEqualTo(Capability.all());
    assertThat(progress.rejected()).isEqualTo(MISSING_PREREQUISITE);
    assertThat(progress.rejectionReason()).contains("requires");
  }

  @Test
  public void shouldNotWriteViewThatThisBrokerDoesNotSupport() {
    // given - an 8.11 broker that leads the partition after the commit
    capabilities.supported = BASE;
    capabilities.view.set(WITH_FORMAT_CHANGE);
    restart();

    // when
    writeCommit(BASE);

    // then - the only commit is the one written here
    committed(BASE);
    assertThat(
            RecordingExporter.clusterCapabilitiesRecords(ClusterCapabilitiesIntent.COMMIT)
                .onlyCommands()
                .limit(1)
                .map(r -> r.getValue().getEnabledCapabilities())
                .toList())
        .containsExactly(BASE.encode());
  }

  @Test
  public void shouldRejectCommitOfUnsupportedCapabilities() {
    // given
    capabilities.supported = BASE;
    restart();

    // when
    writeCommit(WITH_FORMAT_CHANGE);

    // then
    final var rejection =
        RecordingExporter.clusterCapabilitiesRecords().onlyCommandRejections().getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("does not support [POC_812_FORMAT_CHANGE]");
  }

  @Test
  public void shouldStopThePartitionWhenReplayingUnsupportedCapabilities() {
    // given - the log has a commit of a capability that this broker does not support, as after a
    // rollback of the broker to an older version
    capabilities.supported = BASE;
    restart();
    engine.writeRecords(
        RecordToWrite.event()
            .clusterCapabilities(
                ClusterCapabilitiesIntent.COMMITTED,
                new ClusterCapabilitiesRecord()
                    .setEnabledCapabilities(WITH_FORMAT_CHANGE.encode())));
    RecordingExporter.clusterCapabilitiesRecords(ClusterCapabilitiesIntent.COMMITTED).getFirst();
    engine.stop();

    // when
    engine.start(StreamProcessorMode.PROCESSING, false);

    // then
    Awaitility.await("the engine guard fails the partition on replay")
        .atMost(Duration.ofSeconds(30))
        .until(() -> engine.getStreamProcessor(1).isFailed());
    assertThat(engine.getProcessingState().getClusterCapabilitiesState().getEnabledCapabilities())
        .isEqualTo(CapabilitySet.EMPTY);
  }

  /** The rule starts the engine before each test; restarting applies the test's preconditions. */
  private void restart() {
    engine.stop();
    engine.start();
  }

  private void writeCommit(final CapabilitySet set) {
    engine.writeRecords(
        RecordToWrite.commandWithoutClaims()
            .clusterCapabilities(
                ClusterCapabilitiesIntent.COMMIT,
                new ClusterCapabilitiesRecord().setEnabledCapabilities(set.encode())));
  }

  private static Record<ClusterCapabilitiesRecordValue> committed(final CapabilitySet set) {
    return RecordingExporter.clusterCapabilitiesRecords(ClusterCapabilitiesIntent.COMMITTED)
        .withRecordType(RecordType.EVENT)
        .withEnabledCapabilities(set.encode())
        .getFirst();
  }

  private static final class TestPartitionCapabilities implements PartitionCapabilities {
    private final TestView view = new TestView();
    private final List<Progress> progress = new CopyOnWriteArrayList<>();
    private volatile CapabilitySet supported = Capability.all();

    @Override
    public CapabilitySet supported() {
      return supported;
    }

    @Override
    public EnabledCapabilitiesView view() {
      return view;
    }

    @Override
    public void onProgress(final Progress progress) {
      this.progress.add(progress);
    }

    Progress lastProgress() {
      assertThat(progress).isNotEmpty();
      return progress.getLast();
    }
  }

  private static final class TestView implements EnabledCapabilitiesView {
    private final List<Consumer<CapabilitySet>> listeners = new CopyOnWriteArrayList<>();
    private volatile CapabilitySet enabled = CapabilitySet.EMPTY;

    void set(final CapabilitySet enabled) {
      this.enabled = enabled;
      listeners.forEach(listener -> listener.accept(enabled));
    }

    @Override
    public CapabilitySet enabled() {
      return enabled;
    }

    @Override
    public void addListener(final Consumer<CapabilitySet> listener) {
      listeners.add(listener);
    }

    @Override
    public void removeListener(final Consumer<CapabilitySet> listener) {
      listeners.remove(listener);
    }
  }
}
