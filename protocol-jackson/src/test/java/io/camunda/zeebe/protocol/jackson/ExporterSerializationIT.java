/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.broker.exporter.debug.DebugHttpExporter;
import io.camunda.zeebe.protocol.jackson.record.AbstractRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.test.exporter.ExporterIntegrationRule;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ExporterSerializationIT {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ExporterIntegrationRule testHarness = new ExporterIntegrationRule();
  private int debugExporterPort;

  @BeforeEach
  void beforeEach() {
    debugExporterPort = SocketUtil.getNextAddress().getPort();
    testHarness.configure(
        "debug", DebugHttpExporter.class, Map.of("port", debugExporterPort, "limit", 3000));
  }

  @AfterEach
  void afterEach() {
    testHarness.stop();
  }

  @Test
  void shouldDeserializeExportedRecords() throws IOException {
    // given
    testHarness.start();

    // when
    testHarness.performSampleWorkload();

    // then
    // collecting all exported records will wait up to 2 seconds before returning, giving us some
    // assumption for now that everything has been exported
    RecordingExporter.setMaximumWaitTime(2_000);
    final var exportedRecords = RecordingExporter.records().collect(Collectors.toList());
    final var exportedCount = exportedRecords.size();
    final var deserializedRecords = fetchJsonRecords(exportedCount);
    assertThat(deserializedRecords).hasSameSizeAs(exportedRecords);

    // since the DebugHttpExporter reverses the order, flip it again to compare against the
    // RecordingExporter
    Collections.reverse(deserializedRecords);

    // convert the exported record to an immutable equivalent so we can make use of equality instead
    // of having to write logical comparators for each generated value; a recursive bean comparator
    // would also work, but unfortunately there is none that I know of
    assertThat(deserializedRecords)
        .zipSatisfy(exportedRecords, this::assertRecordIsLogicallyEquivalent);
  }

  private List<AbstractRecord<?>> fetchJsonRecords(final int expectedCount) throws IOException {
    final var url = new URL("http://localhost:" + debugExporterPort + "/records.json");

    return Awaitility.await("until we have at least " + expectedCount + " records")
        .pollInSameThread()
        .until(
            () -> MAPPER.readerFor(new TypeReference<List<AbstractRecord<?>>>() {}).readValue(url),
            records -> records.size() >= expectedCount);
  }

  // TODO: this is a hacky way to logically compare two beans, as when serialized to JSON they
  //  should produce logically equivalent objects; this should be replaced by a proper logical equal
  //  and/or copy method in the future
  private void assertRecordIsLogicallyEquivalent(
      final AbstractRecord<?> actual, final Record<?> expected) {
    final var actualJson = MAPPER.valueToTree(actual);
    final var expectedJson = MAPPER.valueToTree(expected);

    assertThat(actualJson).isEqualTo(expectedJson);
  }
}
