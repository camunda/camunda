/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import jakarta.json.stream.JsonGenerator;
import java.io.OutputStream;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;

/**
 * Adapter for {@link UpdateOperation} that pre-measures the serialized payload size of the
 * document/upsert fields so that {@link OpensearchBatchRequest} can chunk bulk requests by their
 * actual on-the-wire NDJSON byte size.
 *
 * <p>Note: this is the opensearch-java 2.x compatible variant of the upstream wrapper introduced in
 * #52536. The 2.x client does not expose {@code BinaryData} on bulk operations, so this wrapper
 * keeps the original document/upsert objects intact (the OS transport serializes them at request
 * time) and only performs a separate dry-run serialization through the {@link JsonpMapper} to
 * compute their byte size.
 */
final class BinaryUpdateOperationWrapper {

  private final JsonpMapper jsonpMapper;
  private Object document;
  private Object upsert;
  private Script script;
  private long documentBytes;
  private long upsertBytes;

  BinaryUpdateOperationWrapper(final JsonpMapper jsonpMapper) {
    this.jsonpMapper = jsonpMapper;
  }

  BinaryUpdateOperationWrapper document(final Object value) {
    document = value;
    documentBytes = measureBytes(value);
    return this;
  }

  BinaryUpdateOperationWrapper upsert(final Object value) {
    upsert = value;
    upsertBytes = measureBytes(value);
    return this;
  }

  BinaryUpdateOperationWrapper script(final Script value) {
    script = value;
    return this;
  }

  long payloadBytes() {
    return documentBytes + upsertBytes;
  }

  UpdateOperation<Object> build(
      final String index, final String id, final String routing, final int retryOnConflict) {
    return UpdateOperation.of(
        b -> {
          b.index(index).id(id).routing(routing).retryOnConflict(retryOnConflict);
          if (document != null) {
            b.document(document);
          }
          if (upsert != null) {
            b.upsert(upsert);
          }
          if (script != null) {
            b.script(script);
          }
          return b;
        });
  }

  private long measureBytes(final Object value) {
    if (value == null) {
      return 0L;
    }
    final CountingOutputStream out = new CountingOutputStream();
    try (JsonGenerator generator = jsonpMapper.jsonProvider().createGenerator(out)) {
      jsonpMapper.serialize(value, generator);
    }
    return out.count();
  }

  private static final class CountingOutputStream extends OutputStream {
    private long count;

    @Override
    public void write(final int b) {
      count++;
    }

    @Override
    public void write(final byte[] b, final int off, final int len) {
      count += len;
    }

    long count() {
      return count;
    }
  }
}
