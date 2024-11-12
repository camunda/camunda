/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.mapper;

import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.REPORT_TYPE;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import java.io.IOException;

public class CustomCollectionEntityDeserializer extends StdDeserializer<CollectionEntity> {

  private ObjectMapper objectMapper;

  public CustomCollectionEntityDeserializer(final ObjectMapper objectMapper) {
    super(CollectionEntity.class);
    this.objectMapper = objectMapper;
  }

  public CustomCollectionEntityDeserializer(final Class<?> vc) {
    super(vc);
  }

  @Override
  public CollectionEntity deserialize(final JsonParser jp, final DeserializationContext ctxt)
      throws IOException {
    final JsonNode node = jp.getCodec().readTree(jp);
    final String json = node.toString();
    if (isReport(node)) {
      return objectMapper.readValue(json, ReportDefinitionDto.class);
    } else {
      return objectMapper.readValue(json, DashboardDefinitionRestDto.class);
    }
  }

  private boolean isReport(final JsonNode node) {
    return node.hasNonNull(REPORT_TYPE);
  }
}
