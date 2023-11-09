/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import com.google.common.collect.ImmutableSet;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;

import java.util.Set;

import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COMBINED;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.CREATED;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.LAST_MODIFIED;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.LAST_MODIFIER;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.NAME;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.OWNER;
import static org.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.REPORT_TYPE;
import static org.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.DATA;

public interface ReportWriter {

  Set<String> UPDATABLE_FIELDS = ImmutableSet.of(
    NAME, DATA, LAST_MODIFIED, LAST_MODIFIER, CREATED, OWNER, COLLECTION_ID, COMBINED, REPORT_TYPE
  );

  String PROCESS_DEFINITION_PROPERTY = String.join(
    ".", DATA, SingleReportDataDto.Fields.definitions, ReportDataDefinitionDto.Fields.key
  );

  IdResponseDto createNewCombinedReport(@NonNull final String userId,
                                        @NonNull final CombinedReportDataDto reportData,
                                        @NonNull final String reportName,
                                        final String description,
                                        final String collectionId);

  IdResponseDto createNewSingleProcessReport(final String userId,
                                             @NonNull final ProcessReportDataDto reportData,
                                             @NonNull final String reportName,
                                             final String description,
                                             final String collectionId);

  IdResponseDto createNewSingleDecisionReport(@NonNull final String userId,
                                              @NonNull final DecisionReportDataDto reportData,
                                              @NonNull final String reportName,
                                              final String description,
                                              final String collectionId);

  void updateSingleProcessReport(final SingleProcessReportDefinitionUpdateDto reportUpdate);

  void updateSingleDecisionReport(final SingleDecisionReportDefinitionUpdateDto reportUpdate);

  void updateCombinedReport(final ReportDefinitionUpdateDto updatedReport);

  void updateProcessDefinitionXmlForProcessReportsWithKey(final String definitionKey,
                                                          final String definitionXml);

  void deleteAllManagementReports();

  void deleteSingleReport(final String reportId);

  void removeSingleReportFromCombinedReports(final String reportId);

  void deleteCombinedReport(final String reportId);

  void deleteAllReportsOfCollection(String collectionId);

}
