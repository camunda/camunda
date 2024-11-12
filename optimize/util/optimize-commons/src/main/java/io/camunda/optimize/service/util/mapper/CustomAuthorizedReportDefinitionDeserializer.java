/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import java.io.IOException;

public class CustomAuthorizedReportDefinitionDeserializer
    extends StdDeserializer<AuthorizedReportDefinitionResponseDto> {

  private ObjectMapper objectMapper;
  private CustomReportDefinitionDeserializer reportDefinitionDeserializer;

  public CustomAuthorizedReportDefinitionDeserializer(final ObjectMapper objectMapper) {
    this(ReportDefinitionDto.class);
    this.objectMapper = objectMapper;
    this.reportDefinitionDeserializer = new CustomReportDefinitionDeserializer(objectMapper);
  }

  public CustomAuthorizedReportDefinitionDeserializer(final Class<?> vc) {
    super(vc);
  }

  @Override
  public AuthorizedReportDefinitionResponseDto deserialize(
      final JsonParser jp, final DeserializationContext ctxt) throws IOException {
    final JsonNode node = jp.readValueAsTree();
    final ReportDefinitionDto reportDefinitionDto =
        reportDefinitionDeserializer.deserialize(jp, node);
    final AuthorizedEntityDto authorizedEntityDto =
        objectMapper.readValue(node.toString(), AuthorizedEntityDto.class);
    return new AuthorizedReportDefinitionResponseDto(
        reportDefinitionDto, authorizedEntityDto.getCurrentUserRole());
  }
}
