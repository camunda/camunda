/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.persistence.EventBasedProcessDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EventBasedProcessIndex extends StrictIndexMappingCreator {
  public static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.EVENT_BASED_PROCESS_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(EventBasedProcessDto.Fields.id.name())
        .field("type", "keyword")
      .endObject()
      .startObject(EventBasedProcessDto.Fields.name.name())
        .field("type", "keyword")
      .endObject()
      .startObject(EventBasedProcessDto.Fields.xml.name())
        .field("type", "text")
        .field("index", true)
        .field("analyzer", "is_present_analyzer")
      .endObject();
    // @formatter:on
  }

}
