/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import io.camunda.zeebe.exporter.opensearch.dto.BulkIndexAction;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VersionUtil;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

/**
 * Buffers indexing requests of records. Each bulk operation is serialized before being buffered to
 * avoid having to serialize it again on retry.
 */
final class BulkIndexRequest {

  public static final ObjectMapper MAPPER =
      new ObjectMapper()
          .addMixIn(Record.class, RecordSequenceMixin.class)
          .addMixIn(EvaluatedDecisionValue.class, EvaluatedDecisionMixin.class)
          .addMixIn(CommandDistributionRecordValue.class, CommandDistributionMixin.class)
          .enable(Feature.ALLOW_SINGLE_QUOTES);

  private static final ObjectMapper PREVIOUS_VERSION_MAPPER =
      new ObjectMapper()
          .addMixIn(Record.class, RecordSequenceMixin.class)
          .addMixIn(CommandDistributionRecordValue.class, CommandDistributionMixin.class)
          .addMixIn(EvaluatedDecisionValue.class, EvaluatedDecisionMixin.class)
          .enable(Feature.ALLOW_SINGLE_QUOTES);

  // The property of the ES record template to store the sequence of the record.
  private static final String RECORD_SEQUENCE_PROPERTY = "sequence";
  private static final String RECORD_AUTHORIZATIONS_PROPERTY = "authorizations";
  private static final String RECORD_AGENT_PROPERTY = "agent";
  private static final String RECORD_DECISION_EVALUATION_INSTANCE_KEY_PROPERTY =
      "decisionEvaluationInstanceKey";
  private static final String AUTH_INFO_PROPERTY = "authInfo";

  private final List<BulkOperation> operations = new ArrayList<>();

  private BulkIndexAction lastIndexedMetadata;
  private int memoryUsageBytes = 0;

  /**
   * Queues the given record for indexing as a bulk operation. The records are serialized before
   * being added to the buffer to avoid having to serialize them again on retry. These will be
   * flushed to OpenSearch when flush conditions are met, e.g. when the buffer reaches a certain
   * size or after a certain time has passed.
   *
   * <p>The call is a no-op if the last indexed action is the same as the given one.
   *
   * @param action the bulk action to take
   * @param record the record that will be the source of the document
   * @param recordSequence the sequence number of the record
   * @return true if the record was appended to the batch, false if the record is already indexed in
   *     the batch because only one copy of the record is allowed in the batch
   */
  boolean index(
      final BulkIndexAction action, final Record<?> record, final RecordSequence recordSequence) {
    // exit early in case we're retrying the last indexed record again
    if (lastIndexedMetadata != null && lastIndexedMetadata.equals(action)) {
      return false;
    }

    final byte[] source;
    try {
      source = serializeRecord(record, recordSequence);
    } catch (final IOException e) {
      throw new OpensearchExporterException(
          String.format("Failed to serialize record to JSON for indexing action %s", action), e);
    }

    memoryUsageBytes += source.length;
    lastIndexedMetadata = action;
    operations.add(createBulkOperation(action, source));
    return true;
  }

  private static byte[] serializeRecord(final Record<?> record, final RecordSequence recordSequence)
      throws IOException {
    final var mapper =
        isPreviousVersionRecord(record.getBrokerVersion()) ? PREVIOUS_VERSION_MAPPER : MAPPER;
    return mapper
        .writer()
        // Enhance the serialized record by its sequence number. The sequence number is not a part
        // of the record itself but a special property for Elasticsearch. It can be used to limit
        // the number of records when reading from the index, for example, by using a range query.
        // Read https://github.com/camunda/camunda/issues/10568 for details.
        .withAttribute(RECORD_SEQUENCE_PROPERTY, recordSequence.sequence())
        .writeValueAsBytes(record);
  }

  private BulkOperation createBulkOperation(final BulkIndexAction action, final byte[] source) {
    // Use the already serialized JSON bytes to create JsonData, just parsing JSON string and
    // then create JsonData from it.
    final String jsonString = new String(source, StandardCharsets.UTF_8);
    final JsonParser parser = JsonProvider.provider().createParser(new StringReader(jsonString));
    final JsonData document = JsonData.from(parser, new JsonbJsonpMapper());

    return new BulkOperation.Builder()
        .index(
            i ->
                i.index(action.index())
                    .routing(action.routing())
                    .id(action.id())
                    .document(document))
        .build();
  }

  /** Returns the number of operations indexed so far. */
  int size() {
    return operations.size();
  }

  /** Returns an approximate amount of memory used by this buffer. */
  int memoryUsageBytes() {
    return memoryUsageBytes;
  }

  /** Returns true if no operations were indexed, i.e. {@link #size()} is 0, false otherwise. */
  boolean isEmpty() {
    return operations.isEmpty();
  }

  /** Clears the buffer entirely. */
  void clear() {
    operations.clear();
    memoryUsageBytes = 0;
    lastIndexedMetadata = null;
  }

  /** Returns the last action metadata indexed. May be null. */
  BulkIndexAction lastIndexedMetadata() {
    return lastIndexedMetadata;
  }

  /** Returns the currently indexed operations as an unmodifiable shallow copy. */
  List<BulkOperation> bulkOperations() {
    return Collections.unmodifiableList(operations);
  }

  private static boolean isPreviousVersionRecord(final String brokerVersion) {
    final SemanticVersion semanticVersion =
        SemanticVersion.parse(brokerVersion)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Expected to parse valid semantic version, but got [%s]"
                            .formatted(brokerVersion)));
    final int currentMinorVersion =
        VersionUtil.getSemanticVersion()
            .map(SemanticVersion::minor)
            .orElseThrow(
                () -> new IllegalStateException("Expected to have a valid semantic version"));
    return semanticVersion.minor() < currentMinorVersion;
  }

  @JsonAppend(attrs = {@JsonAppend.Attr(value = RECORD_SEQUENCE_PROPERTY)})
  @JsonIgnoreProperties({RECORD_AUTHORIZATIONS_PROPERTY, RECORD_AGENT_PROPERTY})
  private static final class RecordSequenceMixin {}

  @JsonIgnoreProperties({RECORD_DECISION_EVALUATION_INSTANCE_KEY_PROPERTY})
  private static final class EvaluatedDecisionMixin {}

  @JsonIgnoreProperties({AUTH_INFO_PROPERTY})
  private static final class CommandDistributionMixin {}
}
