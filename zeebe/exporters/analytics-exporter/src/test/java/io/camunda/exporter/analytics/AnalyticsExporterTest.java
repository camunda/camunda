/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AnalyticsExporterTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @Nested
  class Configuration {

    @Test
    void shouldRejectMissingEndpoint() {
      // given
      final var config = new AnalyticsExporterConfig();
      config.setEnabled(true);
      config.setEndpoint("");
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("analytics", config));

      // when / then
      assertThatThrownBy(() -> new AnalyticsExporter().configure(context))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptMissingEndpointWhenDisabled() {
      // given
      final var config = new AnalyticsExporterConfig();
      config.setEnabled(false);
      config.setEndpoint("");
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("analytics", config));

      // when / then
      assertThatCode(() -> new AnalyticsExporter().configure(context)).doesNotThrowAnyException();
    }
  }

  @Nested
  class ExportBehavior {

    private ExporterTestController controller;
    private AnalyticsExporter exporter;

    @BeforeEach
    void setUp() {
      controller = new ExporterTestController();
      final var config = new AnalyticsExporterConfig();
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("analytics", config))
              .setPartitionId(1);
      exporter = new AnalyticsExporter();
      exporter.configure(context);
      exporter.open(controller);
    }

    @Test
    void shouldUpdatePositionForUnhandledEventType() {
      // given
      final var record =
          FACTORY.generateRecord(ValueType.JOB, r -> r.withRecordType(RecordType.EVENT));

      // when
      exporter.export(record);

      // then
      assertThat(controller.getPosition()).isEqualTo(record.getPosition());
    }

    @Test
    void shouldUpdatePositionForHandledEvents() {
      // given / when
      final var positions = new ArrayList<Long>(10);
      for (int i = 0; i < 10; i++) {
        final var record =
            FACTORY.generateRecord(
                ValueType.PROCESS_INSTANCE_CREATION,
                r ->
                    r.withRecordType(RecordType.EVENT)
                        .withIntent(ProcessInstanceCreationIntent.CREATED));
        exporter.export(record);
        positions.add(record.getPosition());
      }

      // then
      assertThat(controller.getPosition()).isIn(positions);
    }

    @Test
    void shouldUpdatePositionWhenDisabled() {
      // given
      final var disabledConfig = new AnalyticsExporterConfig();
      disabledConfig.setEnabled(false);
      final var disabledController = new ExporterTestController();
      final var context =
          new ExporterTestContext()
              .setConfiguration(new ExporterTestConfiguration<>("analytics", disabledConfig));
      final var disabledExporter = new AnalyticsExporter();
      disabledExporter.configure(context);
      disabledExporter.open(disabledController);

      final var record = FACTORY.generateRecord(ValueType.JOB);

      // when
      disabledExporter.export(record);

      // then
      assertThat(disabledController.getPosition()).isEqualTo(record.getPosition());
    }
  }
}
