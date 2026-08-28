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
import java.util.List;
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

  // Re-checks the report's current first definition/tenants to make sure the wrong XML is not
  // cleared if other writes have altered the report since the last read.
  String CLEAR_DEFINITION_XML_IF_STILL_MATCHING_SCRIPT =
      "def defs = ctx._source.data.definitions;"
          + "if (defs != null && defs.size() > 0 && defs[0].key == params.key) {"
          + "  def tenantIds = defs[0].tenantIds;"
          + "  if (tenantIds == null || tenantIds.contains(params.tenantId)) {"
          + "    ctx._source.data.configuration.xml = null;"
          + "  }"
          + "}";

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

  IdResponseDto createOrUpdateSingleProcessReport(
      final String reportId,
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

  /**
   * Clears the cached XML for the given report IDs, but only for a report whose first-listed
   * definition still matches {@code processDefinitionKey}/{@code tenantId} at write time. The
   * caller selects candidate {@code reportIds} from a point-in-time read, so a concurrent edit to a
   * report's first definition or tenants between that read and this write must not blank the XML.
   */
  void clearReportDefinitionXmlForReportIds(
      final List<String> reportIds, final String processDefinitionKey, final String tenantId);

  void deleteAllManagementReports();

  void deleteSingleReport(final String reportId);

  void removeSingleReportFromCombinedReports(final String reportId);

  void deleteCombinedReport(final String reportId);

  void deleteAllReportsOfCollection(String collectionId);
}
