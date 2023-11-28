/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import org.camunda.optimize.service.db.writer.ReportWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ReportWriterOS implements ReportWriter {

  @Override
  public IdResponseDto createNewCombinedReport(final @NonNull String userId, final @NonNull CombinedReportDataDto reportData,
                                               final @NonNull String reportName, final String description,
                                               final String collectionId) {
    //todo will be handled in the OPT-7376
    return null;
  }

  @Override
  public IdResponseDto createNewSingleProcessReport(final String userId, final @NonNull ProcessReportDataDto reportData,
                                                    final @NonNull String reportName, final String description,
                                                    final String collectionId) {
    //todo will be handled in the OPT-7376
    return null;
  }

  @Override
  public IdResponseDto createNewSingleDecisionReport(final @NonNull String userId,
                                                     final @NonNull DecisionReportDataDto reportData,
                                                     final @NonNull String reportName, final String description,
                                                     final String collectionId) {
    //todo will be handled in the OPT-7376
    return null;
  }

  @Override
  public void updateSingleProcessReport(final SingleProcessReportDefinitionUpdateDto reportUpdate) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateSingleDecisionReport(final SingleDecisionReportDefinitionUpdateDto reportUpdate) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateCombinedReport(final ReportDefinitionUpdateDto updatedReport) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void updateProcessDefinitionXmlForProcessReportsWithKey(final String definitionKey, final String definitionXml) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void deleteAllManagementReports() {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void deleteSingleReport(final String reportId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void removeSingleReportFromCombinedReports(final String reportId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void deleteCombinedReport(final String reportId) {
    //todo will be handled in the OPT-7376
  }

  @Override
  public void deleteAllReportsOfCollection(final String collectionId) {
    //todo will be handled in the OPT-7376
  }

}
