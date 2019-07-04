/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.indices;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public abstract class AbstractIndexCreator implements IndexCreator {

  private static final Logger logger = LoggerFactory.getLogger(AbstractIndexCreator.class);

  public static final String PARTITION_ID = "partitionId";

  @Override
  public XContentBuilder getSource() throws IOException {
    //TODO copy-pasted from Optimize, we need to check if this settings suit our needs
    XContentBuilder source = jsonBuilder()
      .startObject()
        .field("dynamic", "strict")
        .startObject("properties");
          addProperties(source)
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
    return source;
  }

  protected abstract XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException;

  @Override
  public String getAlias() {
    return  getIndexName() + "alias";
  }

}
