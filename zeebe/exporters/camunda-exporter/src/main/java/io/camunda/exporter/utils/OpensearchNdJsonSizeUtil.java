/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

public final class OpensearchNdJsonSizeUtil {

  private OpensearchNdJsonSizeUtil() {}

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

  public static long measureSingleNdJsonSerializable(
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

  public record PayloadSizeResult(long totalBytes, List<OperationSize> operationSizes) {}

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
