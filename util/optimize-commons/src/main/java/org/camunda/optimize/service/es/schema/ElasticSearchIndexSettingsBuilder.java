/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;

import static org.camunda.optimize.service.db.DatabaseConstants.ANALYSIS_SETTING;
import static org.camunda.optimize.service.db.DatabaseConstants.MAX_NGRAM_DIFF;
import static org.camunda.optimize.service.db.DatabaseConstants.IS_PRESENT_ANALYZER;
import static org.camunda.optimize.service.db.DatabaseConstants.IS_PRESENT_FILTER;
import static org.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NGRAM;
import static org.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NORMALIZER;
import static org.camunda.optimize.service.db.DatabaseConstants.MAPPING_NESTED_OBJECTS_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static org.camunda.optimize.service.db.DatabaseConstants.NGRAM_TOKENIZER;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_REPLICAS_SETTING;
import static org.camunda.optimize.service.db.DatabaseConstants.REFRESH_INTERVAL_SETTING;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

public class ElasticSearchIndexSettingsBuilder {

  public static Settings buildDynamicSettings(ConfigurationService configurationService) throws IOException {
    XContentBuilder builder = jsonBuilder();
    // @formatter:off
    builder
      .startObject();
        addDynamicSettings(configurationService, builder)
      .endObject();
    // @formatter:on
    return toSettings(builder);
  }

  public static Settings buildAllSettings(ConfigurationService configurationService,
                                          IndexMappingCreator<XContentBuilder> indexMappingCreator) throws IOException {
    return Settings.builder().loadFromSource(
      buildAllSettingsAsJson(configurationService, indexMappingCreator),
      XContentType.JSON)
      .build();
  }

  public static String buildAllSettingsAsJson(ConfigurationService configurationService,
                                              IndexMappingCreator<XContentBuilder> indexMappingCreator) throws IOException {
    XContentBuilder builder = jsonBuilder();
    // @formatter:off
    builder
      .startObject();
        addDynamicSettings(configurationService, builder);
        addStaticSettings(indexMappingCreator, configurationService, builder);
        addAnalysis(builder)
      .endObject();
    // @formatter:on
    return Strings.toString(builder);
  }

  private static void addStaticSettings(final IndexMappingCreator<XContentBuilder> indexMappingCreator,
                                        final ConfigurationService configurationService,
                                        final XContentBuilder builder) throws IOException {
    indexMappingCreator.getStaticSettings(builder, configurationService);
  }

  private static XContentBuilder addDynamicSettings(final ConfigurationService configurationService,
                                                    final XContentBuilder builder) throws IOException {
    return builder
      .field(MAX_NGRAM_DIFF, MAX_GRAM - 1)
      .field(REFRESH_INTERVAL_SETTING, configurationService.getElasticSearchConfiguration().getRefreshInterval())
      .field(NUMBER_OF_REPLICAS_SETTING, configurationService.getElasticSearchConfiguration().getNumberOfReplicas())
      .field(MAPPING_NESTED_OBJECTS_LIMIT, configurationService.getElasticSearchConfiguration().getNestedDocumentsLimit());
  }

  private static XContentBuilder addAnalysis(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
    .startObject(ANALYSIS_SETTING)
      .startObject("analyzer")
        .startObject(LOWERCASE_NGRAM)
          .field("type", "custom")
          .field("tokenizer", NGRAM_TOKENIZER)
          .field("filter", "lowercase")
        .endObject()
        // this analyzer is supposed to be used for large text fields for which we only want to
        // query for whether they are empty or not, e.g. the xml of definitions
        // see https://app.camunda.com/jira/browse/OPT-2911
        .startObject(IS_PRESENT_ANALYZER)
          .field("type", "custom")
          .field("tokenizer", "keyword")
          .field("filter", IS_PRESENT_FILTER)
        .endObject()
      .endObject()
      .startObject("normalizer")
        .startObject(LOWERCASE_NORMALIZER)
          .field("type", "custom")
          .field("filter", new String[]{"lowercase"})
        .endObject()
      .endObject()
      .startObject("tokenizer")
        .startObject(NGRAM_TOKENIZER)
          .field("type", "ngram")
          .field("min_gram", 1)
          .field("max_gram", MAX_GRAM)
        .endObject()
      .endObject()
      .startObject("filter")
        .startObject(IS_PRESENT_FILTER)
          .field("type", "truncate")
          .field("length", "1")
        .endObject()
      .endObject()
    .endObject();
    // @formatter:on
  }

  private static Settings toSettings(final XContentBuilder builder) {
    return Settings.builder().loadFromSource(Strings.toString(builder), XContentType.JSON).build();
  }

}
