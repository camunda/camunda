/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices.db;

import static io.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NGRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NORMALIZER;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class EventSequenceCountIndexV4<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final String ID = "id";
  public static final String SOURCE_EVENT = "sourceEvent";
  public static final String TARGET_EVENT = "targetEvent";
  public static final String COUNT = "count";

  public static final String GROUP = "group";
  public static final String SOURCE = "source";
  public static final String EVENT_NAME = "eventName";
  public static final String EVENT_LABEL = "eventLabel";

  public static final String N_GRAM_FIELD = "nGramField";

  public static final int VERSION = 4;

  @Override
  public String getIndexName() {
    return "event-sequence-count-external";
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(
            SOURCE_EVENT,
            p ->
                p.object(
                    k ->
                        k.properties(
                                GROUP,
                                p2 ->
                                    p2.keyword(
                                        k2 ->
                                            k2.fields(
                                                    N_GRAM_FIELD,
                                                    p3 -> p3.text(t -> t.analyzer(LOWERCASE_NGRAM)))
                                                .fields(
                                                    LOWERCASE,
                                                    p3 ->
                                                        p3.keyword(
                                                            t ->
                                                                t.normalizer(
                                                                    LOWERCASE_NORMALIZER)))))
                            .properties(
                                SOURCE,
                                p2 ->
                                    p2.keyword(
                                        k2 ->
                                            k2.fields(
                                                    N_GRAM_FIELD,
                                                    p3 -> p3.text(t -> t.analyzer(LOWERCASE_NGRAM)))
                                                .fields(
                                                    LOWERCASE,
                                                    p3 ->
                                                        p3.keyword(
                                                            t ->
                                                                t.normalizer(
                                                                    LOWERCASE_NORMALIZER)))))
                            .properties(
                                EVENT_NAME,
                                p2 ->
                                    p2.keyword(
                                        k2 ->
                                            k2.fields(
                                                    N_GRAM_FIELD,
                                                    p3 -> p3.text(t -> t.analyzer(LOWERCASE_NGRAM)))
                                                .fields(
                                                    LOWERCASE,
                                                    p3 ->
                                                        p3.keyword(
                                                            t ->
                                                                t.normalizer(
                                                                    LOWERCASE_NORMALIZER)))))
                            .properties(EVENT_LABEL, p2 -> p2.keyword(k2 -> k2))))
        .properties(
            TARGET_EVENT,
            p2 ->
                p2.object(
                    k2 ->
                        k2.properties(GROUP, p3 -> p3.keyword(k3 -> k3))
                            .properties(SOURCE, p3 -> p3.keyword(k3 -> k3))
                            .properties(EVENT_NAME, p3 -> p3.keyword(k3 -> k3))
                            .properties(EVENT_LABEL, p3 -> p3.keyword(k3 -> k3))))
        .properties(COUNT, p -> p.long_(l -> l));
  }
}
