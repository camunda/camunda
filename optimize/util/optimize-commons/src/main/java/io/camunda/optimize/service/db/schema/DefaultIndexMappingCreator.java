/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.DEFAULT_SHARD_NUMBER;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.DynamicTemplate;
import co.elastic.clients.elasticsearch._types.mapping.IndexOptions;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.es.schema.PropertiesAppender;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DefaultIndexMappingCreator<TBuilder>
    implements PropertiesAppender, IndexMappingCreator<TBuilder> {

  public static final String LOWERCASE = "lowercase";
  protected static final String ANALYZER = "analyzer";
  protected static final String NORMALIZER = "normalizer";
  private static final String DYNAMIC_MAPPINGS_VALUE_DEFAULT = "strict";

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private DynamicMapping dynamic = DynamicMapping.Strict;

  public abstract TBuilder addStaticSetting(final String key, final int value, TBuilder builder)
      throws IOException;

  @Override
  public TypeMapping getSource() {
    TypeMapping source = null;
    try {
      source = createMapping();
    } catch (final IOException e) {
      final String message = "Could not add mapping to the index '" + getIndexName() + "'!";
      logger.error(message, e);
    }
    return source;
  }

  @Override
  public TBuilder getStaticSettings(
      final TBuilder builder, final ConfigurationService configurationService) throws IOException {
    return addStaticSetting(NUMBER_OF_SHARDS_SETTING, DEFAULT_SHARD_NUMBER, builder);
  }

  protected TypeMapping createMapping() throws IOException {
    return TypeMapping.of(m -> addDynamicTemplates(addProperties(m.dynamic(dynamic))));
  }

  protected TypeMapping.Builder addDynamicTemplates(final TypeMapping.Builder builder) {
    return builder.dynamicTemplates(
        Map.of(
            "string_template",
            DynamicTemplate.of(
                t ->
                    t.matchMappingType("string")
                        .mapping(m -> m.keyword(k -> k.indexOptions(IndexOptions.Docs)))
                        .pathMatch("*"))));
  }

  public void setDynamic(final DynamicMapping dynamic) {
    this.dynamic = dynamic;
  }
}
