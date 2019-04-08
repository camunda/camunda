/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;


public class DynamicMappingsBuilder {
  protected final static String DYNAMIC_MAPPINGS_VALUE_DEFAULT = "strict";

  public static XContentBuilder createDynamicSettings(final PropertiesAppender appender,
                                                      final String dynamicMappingsValue) throws IOException {
    // @formatter:off
    XContentBuilder content = XContentFactory.jsonBuilder()
      .startObject()
        .field("dynamic", dynamicMappingsValue)
        .startObject("properties");
          appender.addProperties(content)
        .endObject()
        .startArray("dynamic_templates")
          .startObject()
            .startObject("string_template")
              .field("match_mapping_type","string")
              .field("path_match","*")
              .startObject("mapping")
                .field("type","string")
                .startObject("norms")
                  .field("enabled",false)
                .endObject()
                .field("index_options","docs")
              .endObject()
            .endObject()
          .endObject()
        .endArray()
      .endObject();
    // @formatter:on
    return content;
  }

}
