/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.indices;

import java.io.IOException;

import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public abstract class AbstractIndexCreator implements IndexCreator {

  public static final String EAGER_GLOBAL_ORDINALS = "eager_global_ordinals";
  public static final String PARTITION_ID = "partitionId";

  @Autowired
  protected OperateProperties operateProperties;
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
  
  public String getIndexName() {
	    return String.format("%s-%s-%s_", operateProperties.getElasticsearch().getIndexPrefix(), getMainIndexName(),operateProperties.getSchemaVersion());
  }

  protected abstract String getMainIndexName();

@Override
  public String getAlias() {
    return  getIndexName() + "alias";
  }

  @Override
  public boolean needsSeveralShards() {
    return false;
  }

}
