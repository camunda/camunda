/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.db.reader.ReportReader;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ReportReaderOS implements ReportReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public Optional<ReportDefinitionDto> getReport(String reportId) {
    //todo will be handled in the OPT-7230
    return Optional.empty();
  }

  @Override
  public Optional<SingleProcessReportDefinitionRequestDto> getSingleProcessReportOmitXml(final String reportId) {
    //todo will be handled in the OPT-7230
   return Optional.empty();
  }

  @Override
  public Optional<SingleDecisionReportDefinitionRequestDto> getSingleDecisionReportOmitXml(final String reportId) {
    //todo will be handled in the OPT-7230
   return Optional.empty();
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForIdsOmitXml(final List<String> reportIds) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<ReportDefinitionDto> getAllReportsForProcessDefinitionKeyOmitXml(final String definitionKey) {
    //todo will be handled in the OPT-7230
   return new ArrayList<>();
  }

  @Override
  public List<ReportDefinitionDto> getAllPrivateReportsOmitXml() {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<SingleProcessReportDefinitionRequestDto> getAllSingleProcessReportsForIdsOmitXml(final List<String> reportIds) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<ReportDefinitionDto> getReportsForCollectionOmitXml(final String collectionId) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<ReportDefinitionDto> getReportsForCollectionIncludingXml(final String collectionId) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReport(String simpleReportId) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public long getReportCount(final ReportType reportType) {
    //todo will be handled in the OPT-7230
    return 1L;
  }

}
