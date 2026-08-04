/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

/**
 * Backs the server-side web sessions of the Camunda Security Library's stateful OIDC webapp chain
 * (<a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>).
 * The fields mirror CSL's {@code PersistentSession}: the session id, its lifecycle timestamps in
 * epoch millis, the maximum inactive interval in seconds, and the serialized attribute map.
 *
 * <p>Attribute keys are dynamic (Spring Security decides them), so {@code attributes} is a disabled
 * object: it stays in {@code _source} and is never indexed, which keeps the strict mapping intact.
 */
public abstract class WebSessionIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String ID = "id";
  public static final String CREATION_TIME = "creationTime";
  public static final String LAST_ACCESSED_TIME = "lastAccessedTime";
  public static final String MAX_INACTIVE_INTERVAL_SECONDS = "maxInactiveIntervalInSeconds";
  public static final String ATTRIBUTES = "attributes";

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(CREATION_TIME, p -> p.long_(l -> l))
        .properties(LAST_ACCESSED_TIME, p -> p.long_(l -> l))
        .properties(MAX_INACTIVE_INTERVAL_SECONDS, p -> p.long_(l -> l))
        .properties(ATTRIBUTES, p -> p.object(o -> o.enabled(false)));
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.WEB_SESSION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
