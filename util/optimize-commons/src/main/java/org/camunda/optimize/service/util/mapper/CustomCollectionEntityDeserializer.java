/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.REPORT_TYPE;

public class CustomCollectionEntityDeserializer extends StdDeserializer<CollectionEntity> {

  private ObjectMapper objectMapper;

  public CustomCollectionEntityDeserializer(ObjectMapper objectMapper) {
    super(CollectionEntity.class);
    this.objectMapper = objectMapper;
  }

  public CustomCollectionEntityDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public CollectionEntity deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    String json = node.toString();
    if (isReport(node)) {
      return objectMapper.readValue(json, ReportDefinitionDto.class);
    } else {
      return objectMapper.readValue(json, DashboardDefinitionRestDto.class);
    }
  }

  private boolean isReport(JsonNode node) {
    return node.hasNonNull(REPORT_TYPE);
  }
}
