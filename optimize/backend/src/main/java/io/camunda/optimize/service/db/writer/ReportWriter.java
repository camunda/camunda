/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COMBINED;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.CREATED;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.LAST_MODIFIED;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.LAST_MODIFIER;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.NAME;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.OWNER;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.REPORT_TYPE;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.DATA;

import com.google.common.collect.ImmutableSet;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import java.util.Set;

public interface ReportWriter {

  Set<String> UPDATABLE_FIELDS =
      ImmutableSet.of(
          NAME,
          DATA,
          LAST_MODIFIED,
          LAST_MODIFIER,
          CREATED,
          OWNER,
          COLLECTION_ID,
          COMBINED,
          REPORT_TYPE);

  String PROCESS_DEFINITION_PROPERTY =
      String.join(
          ".", DATA, SingleReportDataDto.Fields.definitions, ReportDataDefinitionDto.Fields.key);

  IdResponseDto createNewCombinedReport(
      final String userId,
      final CombinedReportDataDto reportData,
      final String reportName,
      final String description,
      final String collectionId);

  IdResponseDto createNewSingleProcessReport(
      final String userId,
      final ProcessReportDataDto reportData,
      final String reportName,
      final String description,
      final String collectionId);

  IdResponseDto createNewSingleDecisionReport(
      final String userId,
      final DecisionReportDataDto reportData,
      final String reportName,
      final String description,
      final String collectionId);

  void updateSingleProcessReport(final SingleProcessReportDefinitionUpdateDto reportUpdate);

  void updateSingleDecisionReport(final SingleDecisionReportDefinitionUpdateDto reportUpdate);

  void updateCombinedReport(final ReportDefinitionUpdateDto updatedReport);

  void updateProcessDefinitionXmlForProcessReportsWithKey(
      final String definitionKey, final String definitionXml);

  void deleteAllManagementReports();

  void deleteSingleReport(final String reportId);

  void removeSingleReportFromCombinedReports(final String reportId);

  void deleteCombinedReport(final String reportId);

  void deleteAllReportsOfCollection(String collectionId);
}
