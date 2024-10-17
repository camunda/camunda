/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.broker.exporter.util.TestExporterFactory;
import org.junit.jupiter.api.Test;

class ExporterDescriptorTest {

  @Test
  void shouldCheckIfIsSameTypeForSameExporter() {
    // GIVEN we have a descriptor and a factory
    final ExporterDescriptor exporterDescriptor1 =
        new ExporterDescriptor(TestExporterFactory.EXPORTER_ID, new TestExporterFactory());

    // AND we have another descriptor and a factory
    final ExporterDescriptor exporterDescriptor2 =
        new ExporterDescriptor(TestExporterFactory.EXPORTER_ID, new TestExporterFactory());

    // THEN we should get a true
    assertThat(exporterDescriptor1.isSameTypeAs(exporterDescriptor2)).isTrue();
  }

  @Test
  void shouldCheckIfIsNotSameTypeForSameExporter() {
    // GIVEN we have a descriptor and a factory
    final ExporterDescriptor exporterDescriptor1 =
        new ExporterDescriptor(TestExporterFactory.EXPORTER_ID, new TestExporterFactory());

    // AND we have another descriptor and a factory
    final ExporterDescriptor exporterDescriptor2 =
        new ExporterDescriptor("otherExporter", ControlledTestExporter.class, null);

    // THEN we should get a true
    assertThat(exporterDescriptor1.isSameTypeAs(exporterDescriptor2)).isFalse();
  }
}
