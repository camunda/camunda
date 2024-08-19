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

import io.camunda.optimize.service.db.es.schema.PropertiesAppender;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DefaultIndexMappingCreator<TBuilder>
    implements PropertiesAppender, IndexMappingCreator<TBuilder> {

  public static final String LOWERCASE = "lowercase";
  protected static final String ANALYZER = "analyzer";
  protected static final String NORMALIZER = "normalizer";
  private static final String DYNAMIC_MAPPINGS_VALUE_DEFAULT = "strict";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private String dynamic = DYNAMIC_MAPPINGS_VALUE_DEFAULT;

  public abstract TBuilder addStaticSetting(
      final String key, final int value, TBuilder contentBuilder) throws IOException;

  @Override
  public XContentBuilder getSource() {
    XContentBuilder source = null;
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
      final TBuilder xContentBuilder, final ConfigurationService configurationService)
      throws IOException {
    return addStaticSetting(NUMBER_OF_SHARDS_SETTING, DEFAULT_SHARD_NUMBER, xContentBuilder);
  }

  protected XContentBuilder createMapping() throws IOException {
    // @formatter:off
    XContentBuilder content = XContentFactory.jsonBuilder().startObject().field("dynamic", dynamic);

    content = content.startObject("properties");
    addProperties(content).endObject();

    content = content.startArray("dynamic_templates");
    addDynamicTemplates(content).endArray();

    content = content.endObject();
    // @formatter:on
    return content;
  }

  protected XContentBuilder addDynamicTemplates(final XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
        .startObject()
        .startObject("string_template")
        .field("match_mapping_type", "string")
        .field("path_match", "*")
        .startObject("mapping")
        .field("type", "keyword")
        .field("index_options", "docs")
        .endObject()
        .endObject()
        .endObject();
    // @formatter:on
  }

  public void setDynamic(final String dynamic) {
    this.dynamic = dynamic;
  }
}
