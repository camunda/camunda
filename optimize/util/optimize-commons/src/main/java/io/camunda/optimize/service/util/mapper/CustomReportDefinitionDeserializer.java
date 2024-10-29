/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.mapper;

import static io.camunda.optimize.dto.optimize.ReportType.DECISION;
import static io.camunda.optimize.dto.optimize.ReportType.PROCESS;
import static io.camunda.optimize.dto.optimize.ReportType.valueOf;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COMBINED;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.REPORT_TYPE;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import java.io.IOException;

public class CustomReportDefinitionDeserializer extends StdDeserializer<ReportDefinitionDto> {

  private ObjectMapper objectMapper;

  public CustomReportDefinitionDeserializer(final ObjectMapper objectMapper) {
    this(ReportDefinitionDto.class);
    this.objectMapper = objectMapper;
  }

  public CustomReportDefinitionDeserializer(final Class<?> vc) {
    super(vc);
  }

  @Override
  public ReportDefinitionDto deserialize(final JsonParser jp, final DeserializationContext ctxt)
      throws IOException {
    final JsonNode node = jp.readValueAsTree();
    return deserialize(jp, node);
  }

  public ReportDefinitionDto deserialize(final JsonParser jp, final JsonNode readJsonTree)
      throws IOException {
    ensureCombinedReportFieldIsProvided(jp, readJsonTree);
    ensureReportTypeFieldIsProvided(jp, readJsonTree);

    final boolean isCombined = readJsonTree.get(COMBINED).booleanValue();
    final String reportTypeAsString = readJsonTree.get(REPORT_TYPE).asText();
    final ReportType reportType = valueOf(reportTypeAsString.toUpperCase());

    final String json = readJsonTree.toString();
    if (isCombined) {
      return objectMapper.readValue(json, CombinedReportDefinitionRequestDto.class);
    } else {
      if (reportType.equals(PROCESS)) {
        return objectMapper.readValue(json, SingleProcessReportDefinitionRequestDto.class);
      } else if (reportType.equals(DECISION)) {
        return objectMapper.readValue(json, SingleDecisionReportDefinitionRequestDto.class);
      }
    }
    final String errorMessage =
        String.format(
            "Could not create single report definition since the report with type [%s] is unknown",
            reportTypeAsString);
    throw new JsonParseException(jp, errorMessage);
  }

  private void ensureCombinedReportFieldIsProvided(final JsonParser jp, final JsonNode node)
      throws JsonParseException {
    if (!node.hasNonNull(COMBINED)) {
      throw new JsonParseException(
          jp, "Could not create report definition since no combined field was provided!");
    }
  }

  private void ensureReportTypeFieldIsProvided(final JsonParser jp, final JsonNode node)
      throws JsonParseException {
    if (!node.hasNonNull(REPORT_TYPE)) {
      throw new JsonParseException(
          jp, "Could not create report definition since no report type field was provided!");
    }
  }
}
