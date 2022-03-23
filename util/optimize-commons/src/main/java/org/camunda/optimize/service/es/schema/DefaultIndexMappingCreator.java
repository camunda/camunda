/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema;

import lombok.Setter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_SHARDS_SETTING;

public abstract class DefaultIndexMappingCreator implements IndexMappingCreator, PropertiesAppender {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private static final String DYNAMIC_MAPPINGS_VALUE_DEFAULT = "strict";
  public static final String LOWERCASE = "lowercase";
  protected static final String ANALYZER = "analyzer";
  protected static final String NORMALIZER = "normalizer";

  @Setter
  private String dynamic = DYNAMIC_MAPPINGS_VALUE_DEFAULT;

  @Override
  public XContentBuilder getSource() {
    XContentBuilder source = null;
    try {
      source = createMapping();
    } catch (IOException e) {
      String message = "Could not add mapping to the index '" + getIndexName() + "'!";
      logger.error(message, e);
    }
    return source;
  }

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    xContentBuilder.field(NUMBER_OF_SHARDS_SETTING, IndexSettingsBuilder.DEFAULT_SHARD_NUMBER);
    return xContentBuilder;
  }

  protected XContentBuilder createMapping() throws IOException {
    // @formatter:off
    XContentBuilder content = XContentFactory.jsonBuilder()
      .startObject()
      .field("dynamic", dynamic);

    content = content
      .startObject("properties");
        addProperties(content)
      .endObject();

    content = content
      .startArray("dynamic_templates");
        addDynamicTemplates(content)
      .endArray();

    content = content.endObject();
    // @formatter:on
    return content;
  }

  protected XContentBuilder addDynamicTemplates(final XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject()
        .startObject("string_template")
          .field("match_mapping_type","string")
          .field("path_match","*")
          .startObject("mapping")
            .field("type","keyword")
            .field("index_options","docs")
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }

}
