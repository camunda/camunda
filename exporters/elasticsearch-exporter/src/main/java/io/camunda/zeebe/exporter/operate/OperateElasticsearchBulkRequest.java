/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.exporter.dto.BulkOperation;
import io.camunda.zeebe.exporter.dto.BulkUpdateAction;
import io.camunda.zeebe.exporter.dto.BulkUpdateSource;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.http.entity.ContentProducer;

/**
 * Buffers indexing requests of records. Each bulk operation is serialized before being buffered to
 * avoid having to serialize it again on retry.
 */
public class OperateElasticsearchBulkRequest implements ContentProducer {

  private static final ObjectMapper MAPPER = NoSpringJacksonConfig.buildObjectMapper();

  // The property of the ES record template to store the sequence of the record.
  private static final String RECORD_SEQUENCE_PROPERTY = "sequence";

  private final List<BulkOperation> operations = new ArrayList<>();

  public void index(String index, OperateEntity entity) {
    index(index, null, entity);
  }

  public void index(String index, String routing, OperateEntity entity) {
    operations.add(new BulkOperation(new BulkIndexAction(index, entity.getId(), routing), entity));
  }

  public void upsert(String index, OperateEntity entity, Map<String, Object> update) {
    upsert(index, null, entity, update);
  }

  public void upsert(
      String index, String routing, OperateEntity entity, Map<String, Object> update) {
    operations.add(
        new BulkOperation(
            new BulkUpdateAction(index, entity.getId(), routing),
            new BulkUpdateSource(update, entity)));
  }

  /** Returns the number of operations indexed so far. */
  int size() {
    return operations.size();
  }

  /** Returns true if no operations were indexed, i.e. {@link #size()} is 0, false otherwise. */
  boolean isEmpty() {
    return operations.isEmpty();
  }

  /** Clears the buffer entirely. */
  void clear() {
    operations.clear();
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
      MAPPER.writeValue(outStream, operation.source());
      outStream.write('\n');
    }
  }
}
