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
          .enable(Feature.ALLOW_SINGLE_QUOTES);

  // The property of the ES record template to store the sequence of the record.
  private static final String RECORD_SEQUENCE_PROPERTY = "sequence";
  private static final String RECORD_AUTHORIZATIONS_PROPERTY = "authorizations";

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
    return MAPPER
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
}
