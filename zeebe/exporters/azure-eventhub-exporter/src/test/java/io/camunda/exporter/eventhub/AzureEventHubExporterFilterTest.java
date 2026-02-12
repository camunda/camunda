/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.eventhub;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.RecordType;
import org.junit.jupiter.api.Test;

class AzureEventHubExporterFilterTest {

  @Test
  void shouldExportEventsByDefault() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setConnectionString("Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=test")
        .setEventHubName("test-hub");

    // when / then
    assertThat(config.shouldIndexRecordType(RecordType.EVENT)).isTrue();
    assertThat(config.shouldIndexRecordType(RecordType.COMMAND)).isFalse();
    assertThat(config.shouldIndexRecordType(RecordType.COMMAND_REJECTION)).isFalse();
  }

  @Test
  void shouldAllowCustomizingRecordTypeFilters() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setConnectionString("Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=test")
        .setEventHubName("test-hub");
    
    // when
    config.index.event = false;
    config.index.command = true;
    config.index.rejection = true;

    // then
    assertThat(config.shouldIndexRecordType(RecordType.EVENT)).isFalse();
    assertThat(config.shouldIndexRecordType(RecordType.COMMAND)).isTrue();
    assertThat(config.shouldIndexRecordType(RecordType.COMMAND_REJECTION)).isTrue();
  }

  @Test
  void shouldReturnFilterIndexConfig() {
    // given
    final var config = new AzureEventHubExporterConfiguration()
        .setConnectionString("Endpoint=sb://test.servicebus.windows.net/;SharedAccessKeyName=test;SharedAccessKey=test")
        .setEventHubName("test-hub");

    // when
    final var indexConfig = config.filterIndexConfig();

    // then
    assertThat(indexConfig).isNotNull();
    assertThat(indexConfig.getVariableNameInclusionExact()).isEmpty();
    assertThat(indexConfig.getVariableNameExclusionExact()).isEmpty();
    assertThat(indexConfig.getVariableValueTypeInclusion()).isEmpty();
    assertThat(indexConfig.getVariableValueTypeExclusion()).isEmpty();
  }
}
