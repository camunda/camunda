/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ExporterEnableTest {
  private static final String EXPORTER_ID_1 = "exporter-1";
  private static final String EXPORTER_ID_2 = "exporter-2";

  @Rule public final ExporterRule rule = ExporterRule.activeExporter();
  private final Map<String, ControlledTestExporterWithMetadata> exporters = new HashMap<>();
  private final List<ExporterDescriptor> exporterDescriptors = new ArrayList<>();

  @Before
  public void init() {
    exporters.clear();
    exporterDescriptors.clear();

    createExporter(EXPORTER_ID_1, Collections.singletonMap("x", 1));
  }

  @After
  public void tearDown() throws Exception {
    rule.closeExporterDirector();
  }

  private ExporterDescriptor createExporter(
      final String exporterId, final Map<String, Object> arguments) {
    return createExporter(exporterId, arguments, null);
  }

  private ExporterDescriptor createExporter(
      final String exporterId,
      final Map<String, Object> arguments,
      final Context.RecordFilter filter) {
    final var exporter = spy(new ControlledTestExporterWithMetadata().withFilter(filter));

    final ExporterDescriptor descriptor =
        spy(new ExporterDescriptor(exporterId, exporter.getClass(), arguments));
    doAnswer(c -> exporter).when(descriptor).newInstance();

    exporters.put(exporterId, exporter);
    exporterDescriptors.add(descriptor);
    return descriptor;
  }

  @Test
  public void shouldEnableExporter() {
    // given
    rule.startExporterDirector(exporterDescriptors);
    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords()).hasSize(1));

    // when
    final var descriptor = createExporter(EXPORTER_ID_2, Collections.singletonMap("x", 1));
    rule.getDirector()
        .enableExporter(EXPORTER_ID_2, new ExporterInitializationInfo(1, null), descriptor)
        .join();

    rule.writeEvent(JobIntent.COMPLETED, new JobRecord());

    // then
    waitUntilExportersHaveSeenTheExpectedRecords();
    assertThat(exporters.get(EXPORTER_ID_1).metadata()).isEqualTo(2);
    assertThat(exporters.get(EXPORTER_ID_2).metadata())
        .describedAs("Exporter 2 starts with the default initial metadata")
        .isEqualTo(1);
  }

  @Test
  public void shouldEnableExporterAndInitializeMetadata() {
    // given
    rule.startExporterDirector(exporterDescriptors);
    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords()).hasSize(1));

    // when
    final var descriptor = createExporter(EXPORTER_ID_2, Collections.singletonMap("x", 1));
    rule.getDirector()
        .enableExporter(EXPORTER_ID_2, new ExporterInitializationInfo(1, EXPORTER_ID_1), descriptor)
        .join();

    rule.writeEvent(JobIntent.COMPLETED, new JobRecord());

    // then
    waitUntilExportersHaveSeenTheExpectedRecords();
    assertThat(exporters.get(EXPORTER_ID_2).metadata())
        .describedAs("Exporter 2 starts with the metadata of exporter 1")
        .isEqualTo(2);
  }

  @Test
  public void shouldReEnableExporterWithLatestMetadata() {
    // given
    rule.startExporterDirector(exporterDescriptors);
    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords()).hasSize(1));

    final var descriptor = createExporter(EXPORTER_ID_2, Collections.singletonMap("x", 1));
    rule.getDirector()
        .enableExporter(EXPORTER_ID_2, new ExporterInitializationInfo(1, EXPORTER_ID_1), descriptor)
        .join();
    rule.writeEvent(JobIntent.COMPLETED, new JobRecord());

    // when
    rule.getDirector().removeExporter(EXPORTER_ID_2).join();
    rule.writeEvent(JobIntent.COMPLETED, new JobRecord());
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords()).hasSize(3));

    final var reEnabledDescriptor = createExporter(EXPORTER_ID_2, Collections.singletonMap("x", 1));
    rule.getDirector()
        .enableExporter(
            EXPORTER_ID_2, new ExporterInitializationInfo(2, EXPORTER_ID_1), reEnabledDescriptor)
        .join();
    rule.writeEvent(JobIntent.CREATED, new JobRecord());

    // then
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords()).hasSize(4));
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(exporters.get(EXPORTER_ID_2).getExportedRecords())
                    .hasSize(1)
                    .first()
                    .extracting(Record::getIntent)
                    .isEqualTo(JobIntent.CREATED));
    assertThat(exporters.get(EXPORTER_ID_2).metadata())
        .describedAs("Exporter 2 restarts with the metadata of exporter 1")
        .isEqualTo(4);
  }

  @Test
  public void shouldReStartExportingAfterNewExporterIsEnabledWhenNoExporterIsConfigured() {
    // given
    rule.startExporterDirector(exporterDescriptors);
    rule.getDirector().removeExporter(EXPORTER_ID_1).join();
    rule.getDirector().removeExporter(EXPORTER_ID_2).join();

    // when
    final var newExporterId = "new-exporter";
    final var descriptor = createExporter(newExporterId, Collections.singletonMap("x", 1));
    rule.getDirector()
        .enableExporter(newExporterId, new ExporterInitializationInfo(1, null), descriptor)
        .join();
    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    // then
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(newExporterId).getExportedRecords()).hasSize(1));
  }

  @Test
  public void shouldExportToAnExporterEnabledOnADirectorStartedWithoutExporters() {
    // given - a director that started without any exporter, so its event filter was built from none
    rule.startExporterDirector(List.of());
    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    // when
    final var newExporterId = "new-exporter";
    final var descriptor = createExporter(newExporterId, Collections.singletonMap("x", 1));
    rule.getDirector()
        .enableExporter(newExporterId, new ExporterInitializationInfo(1, null), descriptor)
        .join();
    rule.writeEvent(JobIntent.CREATED, new JobRecord());

    // then - the records written after enabling reach the new exporter instead of being skipped
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(exporters.get(newExporterId).getExportedRecords())
                    .extracting(Record::getIntent)
                    .contains(JobIntent.CREATED));
  }

  @Test
  public void shouldPassRecordsOnlyTheEnabledExporterAcceptsToIt() {
    // given - a director whose only exporter accepts deployments, so its filter rejects jobs
    exporterDescriptors.clear();
    createExporter(EXPORTER_ID_1, Map.of(), acceptingOnly(ValueType.DEPLOYMENT));
    rule.startExporterDirector(exporterDescriptors);

    // when - an exporter accepting jobs is enabled
    final var descriptor = createExporter(EXPORTER_ID_2, Map.of(), acceptingOnly(ValueType.JOB));
    rule.getDirector()
        .enableExporter(EXPORTER_ID_2, new ExporterInitializationInfo(1, null), descriptor)
        .join();
    rule.writeEvent(JobIntent.CREATED, jobRecord("job"));
    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    // then - the filter now covers both exporters: each gets its own kind of record, and only that
    Awaitility.await()
        .untilAsserted(
            () -> {
              assertThat(exporters.get(EXPORTER_ID_2).getExportedRecords())
                  .extracting(Record::getValueType)
                  .containsExactly(ValueType.JOB);
              assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords())
                  .extracting(Record::getValueType)
                  .containsExactly(ValueType.DEPLOYMENT);
            });
  }

  @Test
  public void shouldExportOnlyWhileTheExporterIsEnabledWhenItIsEnabledDisabledAndEnabledAgain() {
    // given - a director whose other exporter rejects jobs, so only the job exporter accepts them
    exporterDescriptors.clear();
    createExporter(EXPORTER_ID_1, Map.of(), acceptingOnly(ValueType.DEPLOYMENT));
    rule.startExporterDirector(exporterDescriptors);

    // when - on, off, on, writing a job after each step
    rule.getDirector()
        .enableExporter(
            EXPORTER_ID_2,
            new ExporterInitializationInfo(1, null),
            createExporter(EXPORTER_ID_2, Map.of(), acceptingOnly(ValueType.JOB)))
        .join();
    rule.writeEvent(JobIntent.CREATED, jobRecord("while-on-1"));
    Awaitility.await()
        .untilAsserted(() -> assertThat(jobTypesExportedTo(EXPORTER_ID_2)).hasSize(1));

    final var firstInstance = exporters.get(EXPORTER_ID_2);
    rule.getDirector().removeExporter(EXPORTER_ID_2).join();
    rule.writeEvent(JobIntent.CREATED, jobRecord("while-off"));
    // the deployment is read after the job, so seeing it proves the job was already passed over
    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords()).hasSize(1));

    rule.getDirector()
        .enableExporter(
            EXPORTER_ID_2,
            new ExporterInitializationInfo(1, null),
            createExporter(EXPORTER_ID_2, Map.of(), acceptingOnly(ValueType.JOB)))
        .join();
    rule.writeEvent(JobIntent.CREATED, jobRecord("while-on-2"));

    // then - the re-enabled exporter gets what is written after it is enabled again, and the
    // record written while it was disabled reached neither instance
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(jobTypesExportedTo(EXPORTER_ID_2)).containsExactly("while-on-2"));
    assertThat(
            firstInstance.getExportedRecords().stream()
                .map(r -> ((JobRecordValue) r.getValue()).getType()))
        .containsExactly("while-on-1");
  }

  @Test
  public void shouldNotKeepPassingRecordsOnceTheOnlyExporterAcceptingThemIsDisabled() {
    // given - an exporter accepting deployments, and one accepting jobs that gets disabled again
    exporterDescriptors.clear();
    createExporter(EXPORTER_ID_1, Map.of(), acceptingOnly(ValueType.DEPLOYMENT));
    rule.startExporterDirector(exporterDescriptors);
    rule.getDirector()
        .enableExporter(
            EXPORTER_ID_2,
            new ExporterInitializationInfo(1, null),
            createExporter(EXPORTER_ID_2, Map.of(), acceptingOnly(ValueType.JOB)))
        .join();
    rule.getDirector().removeExporter(EXPORTER_ID_2).join();

    // when - a job and then a deployment are written
    final var disabled = exporters.get(EXPORTER_ID_2);
    rule.writeEvent(JobIntent.CREATED, jobRecord("after-disable"));
    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    // then - the disabled exporter sees nothing, however long the director keeps reading
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords()).hasSize(1));
    assertThat(disabled.getExportedRecords()).isEmpty();
  }

  @Test
  public void shouldRetryExporterEnableIfCalledWithRetry() {
    // given
    rule.startExporterDirector(exporterDescriptors);
    rule.getDirector().removeExporter(EXPORTER_ID_2).join();

    final var descriptor = createExporter(EXPORTER_ID_2, Collections.singletonMap("x", 1));
    final var exporter = exporters.get(EXPORTER_ID_2);
    doThrow(new RuntimeException("open failed")).doCallRealMethod().when(exporter).open(any());

    // when
    rule.getDirector()
        .enableExporterWithRetry(EXPORTER_ID_2, new ExporterInitializationInfo(1, null), descriptor)
        .join();

    // then
    Awaitility.await("exporter open has been retried")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> verify(exporter, times(2)).open(any()));
  }

  private static Context.RecordFilter acceptingOnly(final ValueType valueType) {
    return new Context.RecordFilter() {
      @Override
      public boolean acceptType(final RecordType recordType) {
        return true;
      }

      @Override
      public boolean acceptValue(final ValueType value) {
        return value == valueType;
      }
    };
  }

  private static JobRecord jobRecord(final String type) {
    return new JobRecord().setType(type);
  }

  private List<String> jobTypesExportedTo(final String exporterId) {
    return exporters.get(exporterId).getExportedRecords().stream()
        .map(r -> ((JobRecordValue) r.getValue()).getType())
        .toList();
  }

  private void waitUntilExportersHaveSeenTheExpectedRecords() {
    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords()).hasSize(2));
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(exporters.get(EXPORTER_ID_2).getExportedRecords())
                    .hasSize(1)
                    .first()
                    .extracting(Record::getIntent)
                    .isEqualTo(JobIntent.COMPLETED));
  }

  static class ControlledTestExporterWithMetadata implements Exporter {
    private final List<Record<?>> exportedRecords = new CopyOnWriteArrayList<>();
    private Controller controller;
    private int metadata = 0;
    private Context.RecordFilter filter;

    public ControlledTestExporterWithMetadata withFilter(final Context.RecordFilter filter) {
      this.filter = filter;
      return this;
    }

    public List<Record<?>> getExportedRecords() {
      return exportedRecords;
    }

    @Override
    public void configure(final Context context) {
      if (filter != null) {
        context.setFilter(filter);
      }
    }

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
      controller.readMetadata().ifPresent(b -> metadata = ByteBuffer.wrap(b).getInt());
    }

    @Override
    public void export(final Record<?> record) {
      final Record<?> copiedRecord = record.copyOf();
      exportedRecords.add(copiedRecord);
      metadata++;
      controller.updateLastExportedRecordPosition(
          copiedRecord.getPosition(), ByteBuffer.allocate(4).putInt(metadata).array());
    }

    public int metadata() {
      return metadata;
    }
  }
}
