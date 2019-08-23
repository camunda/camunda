/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.templates;

import java.io.IOException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component
public abstract class AbstractTemplateCreator implements TemplateCreator {

  private static final Logger logger = LoggerFactory.getLogger(AbstractTemplateCreator.class);

  public static final String PARTITION_ID = "partitionId";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public XContentBuilder getSource() throws IOException {

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

  @Override
  public String getMainIndexName() {
    return String.format("%s-%s_", operateProperties.getElasticsearch().getIndexPrefix(), getIndexNameFormat());
  }

  @Override
  public String getTemplateName() {
    return getMainIndexName() + "template";
  }

  @Override
  public String getAlias() {
    return getMainIndexName() + "alias";
  }

  @Override
  public String getIndexPattern() {
    return getMainIndexName() + "*";
  }

  protected abstract String getIndexNameFormat();

  protected abstract XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException;

  @Override
  public boolean needsSeveralShards() {
    return true;
  }
}
