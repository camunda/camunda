/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.DATA;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import java.util.List;
import java.util.Optional;

public interface ReportReader {

  String REPORT_DATA_XML_PROPERTY =
      String.join(
          ".",
          DATA,
          SingleReportDataDto.Fields.configuration,
          SingleReportConfigurationDto.Fields.xml);
  String[] REPORT_LIST_EXCLUDES = {REPORT_DATA_XML_PROPERTY};
  String[] ALL_REPORT_INDICES = {
    SINGLE_PROCESS_REPORT_INDEX_NAME, SINGLE_DECISION_REPORT_INDEX_NAME, COMBINED_REPORT_INDEX_NAME
  };

  Optional<ReportDefinitionDto> getReport(String reportId);

  Optional<SingleProcessReportDefinitionRequestDto> getSingleProcessReportOmitXml(
      final String reportId);

  Optional<SingleDecisionReportDefinitionRequestDto> getSingleDecisionReportOmitXml(
      final String reportId);

  List<ReportDefinitionDto> getAllReportsForIdsOmitXml(final List<String> reportIds);

  List<ReportDefinitionDto> getAllReportsForProcessDefinitionKeyOmitXml(final String definitionKey);

  List<ReportDefinitionDto> getAllPrivateReportsOmitXml();

  List<ReportDefinitionDto> getReportsForCollectionIncludingXml(final String collectionId);

  List<SingleProcessReportDefinitionRequestDto> getAllSingleProcessReportsForIdsOmitXml(
      final List<String> reportIds);

  List<ReportDefinitionDto> getReportsForCollectionOmitXml(String collectionId);

  List<CombinedReportDefinitionRequestDto> getCombinedReportsForSimpleReport(String simpleReportId);

  long getReportCount(final ReportType reportType);

  long getUserTaskReportCount();
}
