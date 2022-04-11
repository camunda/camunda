/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.mapper;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;

import java.io.IOException;

import static org.camunda.optimize.dto.optimize.ReportType.DECISION;
import static org.camunda.optimize.dto.optimize.ReportType.PROCESS;
import static org.camunda.optimize.dto.optimize.ReportType.valueOf;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.COMBINED;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.REPORT_TYPE;

public class CustomReportDefinitionDeserializer extends StdDeserializer<ReportDefinitionDto> {

  private ObjectMapper objectMapper;

  public CustomReportDefinitionDeserializer(ObjectMapper objectMapper) {
    this(ReportDefinitionDto.class);
    this.objectMapper = objectMapper;
  }

  public CustomReportDefinitionDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public ReportDefinitionDto deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.readValueAsTree();
    return deserialize(jp, node);
  }

  public ReportDefinitionDto deserialize(final JsonParser jp, final JsonNode readJsonTree) throws IOException {
    ensureCombinedReportFieldIsProvided(jp, readJsonTree);
    ensureReportTypeFieldIsProvided(jp, readJsonTree);

    boolean isCombined = readJsonTree.get(COMBINED).booleanValue();
    String reportTypeAsString = readJsonTree.get(REPORT_TYPE).asText();
    ReportType reportType = valueOf(reportTypeAsString.toUpperCase());

    String json = readJsonTree.toString();
    if (isCombined) {
      return objectMapper.readValue(json, CombinedReportDefinitionRequestDto.class);
    } else {
      if (reportType.equals(PROCESS)) {
        return objectMapper.readValue(json, SingleProcessReportDefinitionRequestDto.class);
      } else if (reportType.equals(DECISION)) {
        return objectMapper.readValue(json, SingleDecisionReportDefinitionRequestDto.class);
      }
    }
    String errorMessage = String.format(
      "Could not create single report definition since the report with type [%s] is unknown", reportTypeAsString
    );
    throw new JsonParseException(jp, errorMessage);
  }

  private void ensureCombinedReportFieldIsProvided(JsonParser jp, JsonNode node) throws JsonParseException {
    if (!node.hasNonNull(COMBINED)) {
      throw new JsonParseException(jp, "Could not create report definition since no combined field was provided!");
    }
  }

  private void ensureReportTypeFieldIsProvided(JsonParser jp, JsonNode node) throws JsonParseException {
    if (!node.hasNonNull(REPORT_TYPE)) {
      throw new JsonParseException(jp, "Could not create report definition since no report type field was provided!");
    }
  }
}
