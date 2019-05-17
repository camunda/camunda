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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.broker.exporter.util.ControlledTestExporter;
import io.zeebe.broker.exporter.util.PojoConfigurationExporter;
import io.zeebe.broker.exporter.util.PojoConfigurationExporter.PojoExporterConfiguration;
import io.zeebe.engine.Loggers;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.verification.VerificationWithTimeout;

public class ExporterDirectorTest {

  private static final int PARTITION_ID = 1;

  private static final String EXPORTER_ID_1 = "exporter-1";
  private static final String EXPORTER_ID_2 = "exporter-2";

  private static final VerificationWithTimeout TIMEOUT = timeout(5_000);

  @Rule public ExporterRule rule = new ExporterRule(PARTITION_ID);

  private final List<ControlledTestExporter> exporters = new ArrayList<>();
  private final List<ExporterDescriptor> exporterDescriptors = new ArrayList<>();

  private ExportersState state;

  @Before
  public void init() {
    exporters.clear();
    exporterDescriptors.clear();

    createExporter(EXPORTER_ID_1, Collections.singletonMap("x", 1));
    createExporter(EXPORTER_ID_2, Collections.singletonMap("y", 2));
  }

  private void createExporter(final String exporterId, final Map<String, Object> arguments) {
    final ControlledTestExporter exporter = spy(new ControlledTestExporter());

    final ExporterDescriptor descriptor =
        spy(new ExporterDescriptor(exporterId, exporter.getClass(), arguments));
    doAnswer(c -> exporter).when(descriptor).newInstance();

    exporters.add(exporter);
    exporterDescriptors.add(descriptor);
  }

  private void startExporterDirector(List<ExporterDescriptor> exporterDescriptors) {
    rule.startExporterDirector(exporterDescriptors);
  }

  @Test
  public void shouldConfigureAllExportersProperlyOnStart() {
    // when
    startExporterDirector(exporterDescriptors);

    // then
    verify(exporters.get(0), TIMEOUT).open(any());
    verify(exporters.get(1), TIMEOUT).open(any());

    exporters.forEach(
        exporter -> {
          assertThat(exporter.getController()).isNotNull();
          assertThat(exporter.getContext().getLogger()).isNotNull();
          assertThat(exporter.getContext().getConfiguration()).isNotNull();
        });

    final Context exporterContext1 = exporters.get(0).getContext();
    assertThat(exporterContext1.getConfiguration().getId()).isEqualTo(EXPORTER_ID_1);
    assertThat(exporterContext1.getConfiguration().getArguments())
        .isEqualTo(Collections.singletonMap("x", 1));
    assertThat(exporterContext1.getLogger().getName())
        .isEqualTo(Loggers.getExporterLogger(EXPORTER_ID_1).getName());

    final Context exporterContext2 = exporters.get(1).getContext();
    assertThat(exporterContext2.getConfiguration().getId()).isEqualTo(EXPORTER_ID_2);
    assertThat(exporterContext2.getConfiguration().getArguments())
        .isEqualTo(Collections.singletonMap("y", 2));
    assertThat(exporterContext2.getLogger().getName())
        .isEqualTo(Loggers.getExporterLogger(EXPORTER_ID_2).getName());
  }

  @Test
  public void shouldCloseAllExportersOnClose() {
    // given
    startExporterDirector(exporterDescriptors);

    // when
    rule.closeExporterDirector();

    // then
    verify(exporters.get(0), TIMEOUT).close();
    verify(exporters.get(1), TIMEOUT).close();
  }

  @Test
  public void shouldInstantiateConfigurationClass() {
    // given
    final String foo = "bar";
    final int x = 123;
    final String bar = "baz";
    final double y = 32.12;

    final Map<String, Object> nested = new HashMap<>();
    nested.put("bar", bar);
    nested.put("y", y);

    final Map<String, Object> arguments = new HashMap<>();
    arguments.put("foo", foo);
    arguments.put("x", x);
    arguments.put("nested", nested);

    final ExporterDescriptor descriptor =
        new ExporterDescriptor(
            "instantiateConfiguration", PojoConfigurationExporter.class, arguments);

    startExporterDirector(Collections.singletonList(descriptor));

    // then
    waitUntil(() -> PojoConfigurationExporter.configuration != null);
    final PojoExporterConfiguration configuration = PojoConfigurationExporter.configuration;

    assertThat(configuration.foo).isEqualTo(foo);
    assertThat(configuration.x).isEqualTo(x);
    assertThat(configuration.nested.bar).isEqualTo(bar);
    assertThat(configuration.nested.y).isEqualTo(y);
  }

  @Test
  public void shouldRetryExportingOnException() {
    // given
    final AtomicLong failCount = new AtomicLong(3);
    exporters
        .get(0)
        .onExport(
            e -> {
              if (failCount.getAndDecrement() > 0) {
                throw new RuntimeException("Export failed (expected)");
              }
            });

    startExporterDirector(exporterDescriptors);

    // when
    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();

    // then
    waitUntil(() -> failCount.get() <= -2);
    assertThat(exporters.get(0).getExportedRecords())
        .extracting(Record::getPosition)
        .containsExactly(eventPosition1, eventPosition2);
    assertThat(exporters.get(1).getExportedRecords())
        .extracting(Record::getPosition)
        .containsExactly(eventPosition1, eventPosition2);
  }

  @Test
  public void shouldExecuteScheduledTask() throws Exception {
    // given
    final CountDownLatch timerTriggerLatch = new CountDownLatch(1);
    final CountDownLatch timerScheduledLatch = new CountDownLatch(1);
    final Duration delay = Duration.ofSeconds(10);

    exporters
        .get(0)
        .onExport(
            r -> {
              exporters.get(0).getController().scheduleTask(delay, timerTriggerLatch::countDown);
              timerScheduledLatch.countDown();
            });

    // when
    startExporterDirector(exporterDescriptors);

    writeEvent();

    timerScheduledLatch.await(5, TimeUnit.SECONDS);

    rule.getClock().addTime(delay);

    // then
    assertThat(timerTriggerLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void shouldRecoverPositionsFromState() {
    // given
    startExporterDirector(exporterDescriptors);

    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();

    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 2);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 2);

    exporters.get(0).getController().updateLastExportedRecordPosition(eventPosition2);
    exporters.get(1).getController().updateLastExportedRecordPosition(eventPosition1);

    rule.closeExporterDirector();
    exporters.get(0).getExportedRecords().clear();
    exporters.get(1).getExportedRecords().clear();

    // then
    startExporterDirector(exporterDescriptors);

    // then
    waitUntil(() -> exporters.get(1).getExportedRecords().size() >= 1);
    assertThat(exporters.get(0).getExportedRecords()).hasSize(0);
    assertThat(exporters.get(1).getExportedRecords())
        .extracting(Record::getPosition)
        .hasSize(1)
        .contains(eventPosition2);
  }

  @Test
  public void shouldUpdateLastExportedPositionOnClose() {
    // given
    startExporterDirector(exporterDescriptors);

    final long eventPosition1 = writeEvent();
    final long eventPosition2 = writeEvent();

    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 2);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 2);

    exporters
        .get(0)
        .onClose(
            () ->
                exporters.get(0).getController().updateLastExportedRecordPosition(eventPosition1));

    // when
    rule.closeExporterDirector();
    exporters.get(0).getExportedRecords().clear();
    exporters.get(1).getExportedRecords().clear();

    startExporterDirector(exporterDescriptors);

    // then
    waitUntil(() -> exporters.get(1).getExportedRecords().size() >= 2);
    assertThat(exporters.get(0).getExportedRecords())
        .extracting(Record::getPosition)
        .hasSize(1)
        .contains(eventPosition2);
    assertThat(exporters.get(1).getExportedRecords())
        .extracting(Record::getPosition)
        .hasSize(2)
        .contains(eventPosition1, eventPosition2);
  }

  @Test
  public void shouldRemoveExporterFromState() {
    // given
    startExporterDirector(exporterDescriptors);

    final long eventPosition = writeEvent();
    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 1);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 1);

    exporters.get(0).getController().updateLastExportedRecordPosition(eventPosition);
    exporters.get(1).getController().updateLastExportedRecordPosition(eventPosition);

    rule.closeExporterDirector();

    // when
    startExporterDirector(Collections.singletonList(exporterDescriptors.get(0)));

    verify(exporters.get(0), TIMEOUT.times(2)).open(any());

    // then
    final ExportersState exportersState = rule.getExportersState();
    assertThat(exportersState.getPosition(EXPORTER_ID_1)).isEqualTo(eventPosition);
    assertThat(exportersState.getPosition(EXPORTER_ID_2)).isEqualTo(-1);
  }

  @Test
  public void shouldRecoverFromStartWithNonUpdatingExporter() {
    // given
    startExporterDirector(exporterDescriptors);
    final long eventPosition = writeEvent();

    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 1);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 1);

    exporters.get(1).getController().updateLastExportedRecordPosition(eventPosition);

    // when
    rule.closeExporterDirector();
    exporters.get(0).getExportedRecords().clear();
    exporters.get(1).getExportedRecords().clear();
    startExporterDirector(exporterDescriptors);

    // then
    waitUntil(() -> exporters.get(0).getExportedRecords().size() >= 1);
    assertThat(exporters.get(0).getExportedRecords())
        .extracting(Record::getPosition)
        .containsExactly(eventPosition);
    assertThat(exporters.get(1).getExportedRecords()).isEmpty();
  }

  private long writeEvent() {
    final DeploymentRecord event = new DeploymentRecord();
    return rule.writeEvent(DeploymentIntent.CREATED, event);
  }
}
