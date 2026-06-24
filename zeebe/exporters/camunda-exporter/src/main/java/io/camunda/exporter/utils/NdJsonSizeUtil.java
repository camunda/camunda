/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.NdJsonpSerializable;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Utility for measuring the serialized NDJSON payload size of Elasticsearch bulk requests. This
 * replicates the wire-format serialization performed by the ES transport layer so that the exporter
 * can log the actual payload size before sending it.
 */
public final class NdJsonSizeUtil {

  private NdJsonSizeUtil() {
    // utility class
  }

  /**
   * Serializes a {@link BulkRequest} to NDJSON and returns a {@link PayloadSizeResult} containing
   * the total byte size and the size of each individual operation. This is the same wire format
   * that the Elasticsearch client sends over HTTP.
   *
   * @param bulkRequest the bulk request to measure
   * @param mapper the {@link JsonpMapper} from the Elasticsearch client ({@code
   *     esClient._jsonpMapper()})
   * @return a result containing total size and per-operation breakdown
   */
  public static PayloadSizeResult measureNdJsonPayloadSize(
      final BulkRequest bulkRequest, final JsonpMapper mapper) {
    final List<BulkOperation> operations = bulkRequest.operations();
    final List<OperationSize> operationSizes = new ArrayList<>(operations.size());
    long totalBytes = 0;

    for (final BulkOperation operation : operations) {
      final long operationBytes = measureSingleNdJsonSerializable(operation, mapper);
      operationSizes.add(OperationSize.of(operation, operationBytes));
      totalBytes += operationBytes;
    }

    return new PayloadSizeResult(totalBytes, Collections.unmodifiableList(operationSizes));
  }

  /**
   * Serializes a single {@link NdJsonpSerializable} value to NDJSON and returns its byte size.
   * Useful for measuring individual operations or other NDJSON-serializable objects.
   */
  private static long measureSingleNdJsonSerializable(
      final NdJsonpSerializable value, final JsonpMapper mapper) {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    writeNdJson(value, baos, mapper);
    return baos.size();
  }

  private static void writeNdJson(
      final NdJsonpSerializable value, final ByteArrayOutputStream baos, final JsonpMapper mapper) {
    final Iterator<?> values = value._serializables();
    while (values.hasNext()) {
      final Object item = values.next();
      if (item == null) {
        continue;
      }
      if (item instanceof NdJsonpSerializable && item != value) {
        writeNdJson((NdJsonpSerializable) item, baos, mapper);
      } else {
        final JsonGenerator generator = mapper.jsonProvider().createGenerator(baos);
        try {
          mapper.serialize(item, generator);
        } finally {
          generator.close();
        }
        baos.write('\n');
      }
    }
  }

  /** The result of measuring a bulk request's NDJSON payload size. */
  public record PayloadSizeResult(long totalBytes, List<OperationSize> operationSizes) {}

  /** The measured NDJSON size of a single bulk operation. */
  record OperationSize(String kind, String index, String id, long bytes) {

    static OperationSize of(final BulkOperation operation, final long bytes) {
      final String kind = operation._kind().jsonValue();
      final String index;
      final String id;

      switch (operation._kind()) {
        case Index -> {
          index = operation.index().index();
          id = operation.index().id();
        }
        case Create -> {
          index = operation.create().index();
          id = operation.create().id();
        }
        case Update -> {
          index = operation.update().index();
          id = operation.update().id();
        }
        case Delete -> {
          index = operation.delete().index();
          id = operation.delete().id();
        }
        default -> {
          index = null;
          id = null;
        }
      }
      return new OperationSize(kind, index, id, bytes);
    }
  }
}
