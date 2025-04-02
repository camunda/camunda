/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.COMBINED_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithJsonParams;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static io.camunda.optimize.service.db.schema.index.report.AbstractReportIndex.DESCRIPTION;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.DATA;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORTS;
import static io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex.REPORT_ITEM_ID;
import static io.camunda.optimize.service.db.schema.index.report.SingleProcessReportIndex.MANAGEMENT_REPORT;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.repository.es.TaskRepositoryES;
import io.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import io.camunda.optimize.service.db.writer.ReportWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.json.JsonValue;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ReportWriterES implements ReportWriter {

  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final TaskRepositoryES taskRepositoryES;

  @Override
  public IdResponseDto createNewCombinedReport(
      @NonNull final String userId,
      @NonNull final CombinedReportDataDto reportData,
      @NonNull final String reportName,
      final String description,
      final String collectionId) {
    log.debug("Writing new combined report to Elasticsearch");
    final String id = IdGenerator.getNextId();
    final CombinedReportDefinitionRequestDto reportDefinitionDto =
        new CombinedReportDefinitionRequestDto();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(reportName);
    reportDefinitionDto.setDescription(description);
    reportDefinitionDto.setData(reportData);
    reportDefinitionDto.setCollectionId(collectionId);

    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, COMBINED_REPORT_INDEX_NAME)
                          .id(id)
                          .document(reportDefinitionDto)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Result.Created)) {
        final String message = "Could not write report to Elasticsearch. ";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }

      log.debug("Report with id [{}] has successfully been created.", id);
      return new IdResponseDto(id);
    } catch (final IOException e) {
      final String errorMessage = "Was not able to insert combined report.!";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public IdResponseDto createNewSingleProcessReport(
      final String userId,
      @NonNull final ProcessReportDataDto reportData,
      @NonNull final String reportName,
      final String description,
      final String collectionId) {
    log.debug("Writing new single report to Elasticsearch");

    final String id = IdGenerator.getNextId();
    final SingleProcessReportDefinitionRequestDto reportDefinitionDto =
        new SingleProcessReportDefinitionRequestDto();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(reportName);
    reportDefinitionDto.setDescription(description);
    reportDefinitionDto.setData(reportData);
    reportDefinitionDto.setCollectionId(collectionId);

    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, SINGLE_PROCESS_REPORT_INDEX_NAME)
                          .id(id)
                          .document(reportDefinitionDto)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Result.Created)) {
        final String message = "Could not write single process report to Elasticsearch.";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }

      log.debug("Single process report with id [{}] has successfully been created.", id);
      return new IdResponseDto(id);
    } catch (final IOException e) {
      final String errorMessage = "Was not able to insert single process report.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public IdResponseDto createNewSingleDecisionReport(
      @NonNull final String userId,
      @NonNull final DecisionReportDataDto reportData,
      @NonNull final String reportName,
      final String description,
      final String collectionId) {
    log.debug("Writing new single report to Elasticsearch");

    final String id = IdGenerator.getNextId();
    final SingleDecisionReportDefinitionRequestDto reportDefinitionDto =
        new SingleDecisionReportDefinitionRequestDto();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(reportName);
    reportDefinitionDto.setDescription(description);
    reportDefinitionDto.setData(reportData);
    reportDefinitionDto.setCollectionId(collectionId);

    try {
      final IndexResponse indexResponse =
          esClient.index(
              OptimizeIndexRequestBuilderES.of(
                  i ->
                      i.optimizeIndex(esClient, SINGLE_DECISION_REPORT_INDEX_NAME)
                          .id(id)
                          .document(reportDefinitionDto)
                          .refresh(Refresh.True)));

      if (!indexResponse.result().equals(Result.Created)) {
        final String message = "Could not write single decision report to Elasticsearch.";
        log.error(message);
        throw new OptimizeRuntimeException(message);
      }

      log.debug("Single decision report with id [{}] has successfully been created.", id);
      return new IdResponseDto(id);
    } catch (final IOException e) {
      final String errorMessage = "Was not able to insert single decision report.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void updateSingleProcessReport(final SingleProcessReportDefinitionUpdateDto reportUpdate) {
    updateReport(reportUpdate, SINGLE_PROCESS_REPORT_INDEX_NAME);
  }

  @Override
  public void updateSingleDecisionReport(
      final SingleDecisionReportDefinitionUpdateDto reportUpdate) {
    updateReport(reportUpdate, SINGLE_DECISION_REPORT_INDEX_NAME);
  }

  @Override
  public void updateCombinedReport(final ReportDefinitionUpdateDto updatedReport) {
    updateReport(updatedReport, COMBINED_REPORT_INDEX_NAME);
  }

  @Override
  public void updateProcessDefinitionXmlForProcessReportsWithKey(
      final String definitionKey, final String definitionXml) {
    final String updateItem = String.format("reports with definitionKey [%s]", definitionKey);
    log.debug("Updating definition XML in {} in Elasticsearch", updateItem);

    final Script updateDefinitionXmlScript =
        Script.of(
            i ->
                i.lang(ScriptLanguage.Painless)
                    // this script is deliberately not updating the modified date as this is
                    // no user operation
                    .source("ctx._source.data.configuration.xml = params.newXml;")
                    .params(Collections.singletonMap("newXml", JsonData.of(definitionXml))));

    taskRepositoryES.tryUpdateByQueryRequest(
        updateItem,
        updateDefinitionXmlScript,
        Query.of(q -> q.term(t -> t.field(PROCESS_DEFINITION_PROPERTY).value(definitionKey))),
        SINGLE_PROCESS_REPORT_INDEX_NAME);
  }

  @Override
  public void deleteAllManagementReports() {
    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(q -> q.term(t -> t.field(DATA + "." + MANAGEMENT_REPORT).value(true))),
        "all management reports",
        true,
        SINGLE_PROCESS_REPORT_INDEX_NAME);
  }

  @Override
  public void deleteSingleReport(final String reportId) {
    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(q -> q.ids(i -> i.values(reportId))),
        String.format("single report with ID [%s]", reportId),
        true,
        SINGLE_PROCESS_REPORT_INDEX_NAME,
        SINGLE_DECISION_REPORT_INDEX_NAME);
  }

  @Override
  public void removeSingleReportFromCombinedReports(final String reportId) {
    final String updateItemName = String.format("report with ID [%s]", reportId);
    log.info("Removing {} from combined report.", updateItemName);

    final Script removeReportIdFromCombinedReportsScript =
        Script.of(
            i ->
                i.lang(ScriptLanguage.Painless)
                    .source(
                        "def reports = ctx._source.data.reports;"
                            + "if(reports != null) {"
                            + "  reports.removeIf(r -> r.id.equals(params.idToRemove));"
                            + "}")
                    .params(Map.of("idToRemove", JsonData.of(reportId))));

    taskRepositoryES.tryUpdateByQueryRequest(
        updateItemName,
        removeReportIdFromCombinedReportsScript,
        Query.of(
            q ->
                q.nested(
                    n ->
                        n.path(DATA)
                            .scoreMode(ChildScoreMode.None)
                            .query(
                                qq ->
                                    qq.nested(
                                        nn ->
                                            nn.path(String.join(".", DATA, REPORTS))
                                                .scoreMode(ChildScoreMode.None)
                                                .query(
                                                    q2 ->
                                                        q2.term(
                                                            t ->
                                                                t.field(
                                                                        String.join(
                                                                            ".",
                                                                            DATA,
                                                                            REPORTS,
                                                                            REPORT_ITEM_ID))
                                                                    .value(reportId))))))),
        COMBINED_REPORT_INDEX_NAME);
  }

  @Override
  public void deleteCombinedReport(final String reportId) {
    log.debug("Deleting combined report with id [{}]", reportId);
    final DeleteResponse deleteResponse;
    try {
      deleteResponse =
          esClient.delete(
              OptimizeDeleteRequestBuilderES.of(
                  d ->
                      d.optimizeIndex(esClient, COMBINED_REPORT_INDEX_NAME)
                          .id(reportId)
                          .refresh(Refresh.True)));
    } catch (final IOException e) {
      final String reason =
          String.format("Could not delete combined report with id [%s].", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.result().equals(Result.Deleted)) {
      final String message =
          String.format(
              "Could not delete combined process report with id [%s]. "
                  + "Combined process report does not exist."
                  + "Maybe it was already deleted by someone else?",
              reportId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  @Override
  public void deleteAllReportsOfCollection(final String collectionId) {
    taskRepositoryES.tryDeleteByQueryRequest(
        Query.of(q -> q.term(t -> t.field(COLLECTION_ID).value(collectionId))),
        String.format("all reports of collection with collectionId [%s]", collectionId),
        true,
        COMBINED_REPORT_INDEX_NAME,
        SINGLE_PROCESS_REPORT_INDEX_NAME,
        SINGLE_DECISION_REPORT_INDEX_NAME);
  }

  private void updateReport(final ReportDefinitionUpdateDto updatedReport, final String indexName) {
    log.debug("Updating report with id [{}] in Elasticsearch", updatedReport.getId());
    try {
      final Map<String, JsonData> updateParams =
          DatabaseWriterUtil.createFieldUpdateScriptParams(
                  UPDATABLE_FIELDS, updatedReport, objectMapper)
              .entrySet()
              .stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonData.of(e.getValue())));
      // We always update the description, even if the new value is null
      final JsonData descriptionJson =
          updatedReport.getDescription() == null
              ? JsonData.of(JsonValue.NULL)
              : JsonData.of(updatedReport.getDescription());
      updateParams.put(DESCRIPTION, descriptionJson);
      final Script updateScript =
          createDefaultScriptWithJsonParams(
              DatabaseWriterUtil.createUpdateFieldsScript(updateParams.keySet()), updateParams);

      final UpdateResponse<?> updateResponse =
          esClient.update(
              OptimizeUpdateRequestBuilderES.of(
                  b ->
                      b.optimizeIndex(esClient, indexName)
                          .id(updatedReport.getId())
                          .script(updateScript)
                          .refresh(Refresh.True)
                          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)),
              ReportDefinitionUpdateDto.class);
      if (updateResponse.shards().failed().intValue() > 0) {
        log.error(
            "Was not able to update report with id [{}] and name [{}].",
            updatedReport.getId(),
            updatedReport.getName());
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (final IOException e) {
      final String errorMessage =
          String.format("Was not able to update report with id [%s].", updatedReport.getId());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }
}
