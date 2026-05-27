/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.util.BinaryData;

final class BinaryUpdateOperationWrapper {

  private final JsonpMapper jsonpMapper;
  private BinaryData document;
  private BinaryData upsert;
  private Script script;

  BinaryUpdateOperationWrapper(final JsonpMapper jsonpMapper) {
    this.jsonpMapper = jsonpMapper;
  }

  BinaryUpdateOperationWrapper document(final Object value) {
    document = BinaryData.of(value, jsonpMapper);
    return this;
  }

  BinaryUpdateOperationWrapper upsert(final Object value) {
    upsert = BinaryData.of(value, jsonpMapper);
    return this;
  }

  BinaryUpdateOperationWrapper script(final Script value) {
    script = value;
    return this;
  }

  long payloadBytes() {
    long total = 0L;
    if (document != null) {
      total += document.size();
    }
    if (upsert != null) {
      total += upsert.size();
    }
    return total;
  }

  UpdateOperation<BinaryData> build(
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
}
