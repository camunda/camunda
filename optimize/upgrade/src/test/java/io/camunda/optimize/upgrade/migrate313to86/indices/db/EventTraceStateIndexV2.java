/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices.db;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class EventTraceStateIndexV2<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final String TRACE_ID = "traceId";
  public static final String EVENT_TRACE = "eventTrace";

  public static final String EVENT_ID = "eventId";
  public static final String GROUP = "group";
  public static final String SOURCE = "source";
  public static final String EVENT_NAME = "eventName";
  public static final String TIMESTAMP = "timestamp";
  public static final String ORDER_COUNTER = "orderCounter";

  public static final int VERSION = 2;

  @Override
  public String getIndexName() {
    return "event-trace-state-external";
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(TRACE_ID, p -> p.keyword(k -> k))
        .properties(
            EVENT_TRACE,
            p ->
                p.nested(
                    k ->
                        k.properties(EVENT_ID, p2 -> p2.keyword(k2 -> k2))
                            .properties(GROUP, p2 -> p2.keyword(k2 -> k2))
                            .properties(SOURCE, p2 -> p2.keyword(k2 -> k2))
                            .properties(EVENT_NAME, p2 -> p2.keyword(k2 -> k2))
                            .properties(ORDER_COUNTER, p2 -> p2.keyword(k2 -> k2))
                            .properties(TIMESTAMP, p2 -> p2.date(k2 -> k2))));
  }
}
