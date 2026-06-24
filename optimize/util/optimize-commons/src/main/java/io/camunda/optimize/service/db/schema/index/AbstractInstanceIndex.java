/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.IGNORE_ABOVE_CHAR_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NGRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NORMALIZER;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import io.camunda.optimize.service.db.schema.DynamicIndexable;

public abstract class AbstractInstanceIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder>
    implements DynamicIndexable {

  public static final String MULTIVALUE_FIELD_DATE = "date";
  public static final String MULTIVALUE_FIELD_LONG = "long";
  public static final String MULTIVALUE_FIELD_DOUBLE = "double";
  public static final String N_GRAM_FIELD = "nGramField";
  public static final String LOWERCASE_FIELD = "lowercaseField";

  private final String key;

  protected AbstractInstanceIndex(final String key) {
    this.key = key;
  }

  public abstract String getDefinitionKeyFieldName();

  public abstract String getDefinitionVersionFieldName();

  public abstract String getTenantIdFieldName();

  protected KeywordProperty.Builder addValueMultifields(final KeywordProperty.Builder builder) {
    return builder
        .fields(N_GRAM_FIELD, Property.of(p -> p.text(t -> t.analyzer(LOWERCASE_NGRAM))))
        .fields(
            LOWERCASE_FIELD,
            Property.of(
                p ->
                    p.keyword(
                        t ->
                            t.normalizer(LOWERCASE_NORMALIZER)
                                .ignoreAbove(IGNORE_ABOVE_CHAR_LIMIT))))
        .fields(
            MULTIVALUE_FIELD_DATE,
            Property.of(p -> p.date(t -> t.format(OPTIMIZE_DATE_FORMAT).ignoreMalformed(true))))
        .fields(MULTIVALUE_FIELD_LONG, Property.of(p -> p.long_(t -> t.ignoreMalformed(true))))
        .fields(MULTIVALUE_FIELD_DOUBLE, Property.of(p -> p.double_(t -> t.ignoreMalformed(true))));
  }

  @Override
  public String getKey() {
    return key;
  }
}
