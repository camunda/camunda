/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.http.entity.ContentProducer;

/**
 * Buffers indexing requests of records. Each bulk operation is serialized before being buffered to
 * avoid having to serialize it again on retry.
 */
final class BulkIndexRequest implements ContentProducer {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .addMixIn(Record.class, RecordSequenceMixin.class)
          .addMixIn(UserTaskRecordValue.class, UserTaskMixin.class)
          .addMixIn(EvaluatedDecisionValue.class, EvaluatedDecisionMixin.class)
          .enable(Feature.ALLOW_SINGLE_QUOTES);

  private static final ObjectMapper PREVIOUS_VERSION_MAPPER =
      new ObjectMapper()
          .addMixIn(EvaluatedDecisionValue.class, EvaluatedDecisionMixin.class)
          .addMixIn(Record.class, BatchOperationReferenceRecordMixin.class)
          .addMixIn(ProcessInstanceCreationRecordValue.class, ProcessInstanceCreationMixin.class)
          .addMixIn(ProcessInstanceRecordValue.class, ProcessInstanceMixin.class)
          .addMixIn(ProcessInstanceResultRecordValue.class, ProcessInstanceResultMixin.class)
          .addMixIn(UserTaskRecordValue.class, UserTaskMixin.class)
          .addMixIn(JobRecordValue.class, JobMixin.class)
          .enable(Feature.ALLOW_SINGLE_QUOTES);

  // The property of the ES record template to store the sequence of the record.
  private static final String RECORD_SEQUENCE_PROPERTY = "sequence";
  private static final String RECORD_AUTHORIZATIONS_PROPERTY = "authorizations";
  private static final String RECORD_DECISION_EVALUATION_INSTANCE_KEY_PROPERTY =
      "decisionEvaluationInstanceKey";
  private static final String BATCH_OPERATION_REFERENCE_PROPERTY = "batchOperationReference";
  private static final String TAGS_PROPERTY = "tags";
  private static final String RESULT_PROPERTY = "result";
  private static final String DENIED_REASON_PROPERTY = "deniedReason";
  private static final String RUNTIME_INSTRUCTIONS_PROPERTY = "runtimeInstructions";
  private static final String ELEMENT_INSTANCE_PATH_PROPERTY = "elementInstancePath";
  private static final String PROCESS_DEFINITION_PATH_PROPERTY = "processDefinitionPath";
  private static final String CALLING_ELEMENT_PATH_PROPERTY = "callingElementPath";
  private final List<BulkOperation> operations = new ArrayList<>();
  private BulkIndexAction lastIndexedMetadata;
  private int memoryUsageBytes = 0;

  /**
   * Indexes the given record for the given bulk action. See
   * https://www.elastic.co/guide/en/elasticsearch/reference/7.17/docs-bulk.html for the types of
   * actions.
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
      throw new ElasticsearchExporterException(
          String.format("Failed to serialize record to JSON for indexing action %s", action), e);
    }

    final BulkOperation command = new BulkOperation(action, source);
    memoryUsageBytes += command.source().length;
    lastIndexedMetadata = action;
    operations.add(command);
    return true;
  }

  private static byte[] serializeRecord(final Record<?> record, final RecordSequence recordSequence)
      throws IOException {
    final SemanticVersion semanticVersion =
        SemanticVersion.parse(record.getBrokerVersion())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Expected to parse valid semantic version, but got [%s]"
                            .formatted(record.getBrokerVersion())));
    final var mapper = semanticVersion.minor() < 8 ? PREVIOUS_VERSION_MAPPER : MAPPER;
    return mapper
        .writer()
        // Enhance the serialized record by its sequence number. The sequence number is not a part
        // of the record itself but a special property for Elasticsearch. It can be used to limit
        // the number of records when reading from the index, for example, by using a range query.
        // Read https://github.com/camunda/camunda/issues/10568 for details.
        .withAttribute(RECORD_SEQUENCE_PROPERTY, recordSequence.sequence())
        .writeValueAsBytes(record);
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

  /**
   * Writes the JSON serialized entries, separated by a line ending for each, effectively writing
   * nd-json.
   */
  @Override
  public void writeTo(final OutputStream outStream) throws IOException {
    for (final var operation : operations) {
      MAPPER.writeValue(outStream, operation.metadata());
      outStream.write('\n');
      outStream.write(operation.source());
      outStream.write('\n');
    }
  }

  record BulkOperation(BulkIndexAction metadata, byte[] source) {}

  @JsonAppend(attrs = {@JsonAppend.Attr(value = RECORD_SEQUENCE_PROPERTY)})
  @JsonIgnoreProperties({RECORD_AUTHORIZATIONS_PROPERTY})
  private static final class RecordSequenceMixin {}

  @JsonIgnoreProperties({RECORD_DECISION_EVALUATION_INSTANCE_KEY_PROPERTY})
  private static final class EvaluatedDecisionMixin {}

  @JsonAppend(attrs = {@JsonAppend.Attr(value = RECORD_SEQUENCE_PROPERTY)})
  @JsonIgnoreProperties({BATCH_OPERATION_REFERENCE_PROPERTY, RECORD_AUTHORIZATIONS_PROPERTY})
  private static final class BatchOperationReferenceRecordMixin {}

  @JsonIgnoreProperties({
    TAGS_PROPERTY,
    ELEMENT_INSTANCE_PATH_PROPERTY,
    PROCESS_DEFINITION_PATH_PROPERTY,
    CALLING_ELEMENT_PATH_PROPERTY,
  })
  private static final class ProcessInstanceMixin {}

  @JsonIgnoreProperties({TAGS_PROPERTY, RUNTIME_INSTRUCTIONS_PROPERTY})
  private static final class ProcessInstanceCreationMixin {}

  @JsonIgnoreProperties({TAGS_PROPERTY})
  private static final class ProcessInstanceResultMixin {}

  @JsonIgnoreProperties({DENIED_REASON_PROPERTY})
  private static final class UserTaskMixin {}

  @JsonIgnoreProperties({RESULT_PROPERTY, TAGS_PROPERTY})
  private static final class JobMixin {}
}
