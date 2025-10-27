/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.WorkloadGenerator;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
final class ExporterSerializationTest {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker().withRecordingExporter(true);

  @AutoCloseResource private ZeebeClient client;

  @BeforeEach
  void beforeEach() {
    client = broker.newClientBuilder().build();
  }

  @Test
  void shouldDeserializeExportedRecords() throws IOException {
    // given
    RecordingExporter.setMaximumWaitTime(2_000);
    WorkloadGenerator.performSampleWorkload(client);
    final var exportedRecords = RecordingExporter.records().collect(Collectors.toList());
    final var jsonRecords = exportedRecords.stream().map(Record::toJson).toList();
    final var jsonString = "[" + String.join(",", jsonRecords) + "]";

    // when
    final List<Record<?>> deserializedRecords =
        MAPPER.readerFor(new TypeReference<List<Record<?>>>() {}).readValue(jsonString);

    // then
    // convert the exported record to an immutable equivalent so we can make use of equality instead
    // of having to write logical comparators for each generated value; a recursive bean comparator
    // would also work, but unfortunately there is none that I know of
    assertThat(deserializedRecords)
        .hasSameSizeAs(exportedRecords)
        .zipSatisfy(exportedRecords, this::assertRecordIsLogicallyEquivalent);
  }

  // TODO: this is a hacky way to logically compare two beans, as when serialized to JSON they
  //  should produce logically equivalent objects; this should be replaced by a proper logical equal
  //  and/or copy method in the future
  private void assertRecordIsLogicallyEquivalent(final Record<?> actual, final Record<?> expected) {
    final var actualJson = MAPPER.valueToTree(actual);
    final var expectedJson = MAPPER.valueToTree(expected);

    assertThat(actualJson).isEqualTo(expectedJson);
  }
}
