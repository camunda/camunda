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
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class EventIndexV4<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final String ID = "id";
  public static final String EVENT_NAME = "eventName";
  public static final String TRACE_ID = "traceId";
  public static final String TIMESTAMP = "timestamp";
  public static final String INGESTION_TIMESTAMP = "ingestionTimestamp";
  public static final String GROUP = "group";
  public static final String SOURCE = "source";
  public static final String DATA = "data";
  public static final String N_GRAM_FIELD = "nGramField";

  public static final int VERSION = 4;

  @Override
  public String getIndexName() {
    return "event";
  }

  @Override
  public String getIndexNameInitialSuffix() {
    return DatabaseConstants.INDEX_SUFFIX_PRE_ROLLOVER;
  }

  @Override
  public boolean isCreateFromTemplate() {
    return true;
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
            EVENT_NAME,
            p ->
                p.keyword(
                    k ->
                        k.fields(N_GRAM_FIELD, f -> f.text(t -> t.analyzer(LOWERCASE_NGRAM)))
                            .fields(
                                LOWERCASE,
                                f -> f.keyword(kk -> kk.normalizer(LOWERCASE_NORMALIZER)))))
        .properties(
            TRACE_ID,
            p ->
                p.keyword(
                    k ->
                        k.fields(N_GRAM_FIELD, f -> f.text(t -> t.analyzer(LOWERCASE_NGRAM)))
                            .fields(
                                LOWERCASE,
                                f -> f.keyword(kk -> kk.normalizer(LOWERCASE_NORMALIZER)))))
        .properties(TIMESTAMP, p -> p.date(d -> d))
        .properties(INGESTION_TIMESTAMP, p -> p.date(d -> d))
        .properties(
            GROUP,
            p ->
                p.keyword(
                    k ->
                        k.fields(N_GRAM_FIELD, f -> f.text(t -> t.analyzer(LOWERCASE_NGRAM)))
                            .fields(
                                LOWERCASE,
                                f -> f.keyword(kk -> kk.normalizer(LOWERCASE_NORMALIZER)))))
        .properties(
            SOURCE,
            p ->
                p.keyword(
                    k ->
                        k.fields(N_GRAM_FIELD, f -> f.text(t -> t.analyzer(LOWERCASE_NGRAM)))
                            .fields(
                                LOWERCASE,
                                f -> f.keyword(kk -> kk.normalizer(LOWERCASE_NORMALIZER)))))
        .properties(DATA, p -> p.object(o -> o.enabled(false)));
  }
}
