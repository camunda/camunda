package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import org.camunda.optimize.service.es.schema.type.CombinedReportType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ReportWriter {
  private static final Logger logger = LoggerFactory.getLogger(ReportWriter.class);

  private static final String DEFAULT_REPORT_NAME = "New Report";
  private final ObjectMapper objectMapper;
  private RestHighLevelClient esClient;

  @Autowired
  public ReportWriter(final RestHighLevelClient esClient,
                      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public IdDto createNewCombinedReport(final String userId) {
    logger.debug("Writing new combined report to Elasticsearch");
    final String id = IdGenerator.getNextId();
    final CombinedReportDefinitionDto reportDefinitionDto = new CombinedReportDefinitionDto();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(DEFAULT_REPORT_NAME);

    try {
      IndexRequest request = new IndexRequest(
        getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE),
        COMBINED_REPORT_TYPE,
        id
      )
        .source(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write report to Elasticsearch. ";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }

      logger.debug("Report with id [{}] has successfully been created.", id);
      IdDto idDto = new IdDto();
      idDto.setId(id);
      return idDto;
    } catch (IOException e) {
      String errorMessage = "Was not able to insert combined report.!";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public IdDto createNewSingleProcessReport(final String userId) {
    logger.debug("Writing new single report to Elasticsearch");

    final String id = IdGenerator.getNextId();
    final SingleProcessReportDefinitionDto reportDefinitionDto = new SingleProcessReportDefinitionDto();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = OffsetDateTime.now();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(DEFAULT_REPORT_NAME);

    try {
      IndexRequest request = new IndexRequest(
        getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE), SINGLE_PROCESS_REPORT_TYPE, id
      )
        .source(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write single process report to Elasticsearch.";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }

      logger.debug("Single process report with id [{}] has successfully been created.", id);
      IdDto idDto = new IdDto();
      idDto.setId(id);
      return idDto;
    } catch (IOException e) {
      String errorMessage = "Was not able to insert single process report.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public IdDto createNewSingleDecisionReport(final String userId) {
    logger.debug("Writing new single report to Elasticsearch");

    final String id = IdGenerator.getNextId();
    final SingleDecisionReportDefinitionDto reportDefinitionDto = new SingleDecisionReportDefinitionDto();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = OffsetDateTime.now();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(DEFAULT_REPORT_NAME);

    try {
      IndexRequest request = new IndexRequest(
        getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE), SINGLE_DECISION_REPORT_TYPE, id
      )
        .source(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write single decision report to Elasticsearch.";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }

      logger.debug("Single decision report with id [{}] has successfully been created.", id);
      IdDto idDto = new IdDto();
      idDto.setId(id);
      return idDto;
    } catch (IOException e) {
      String errorMessage = "Was not able to insert single decision report.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void updateSingleProcessReport(final SingleProcessReportDefinitionUpdateDto reportUpdate) {
    updateReport(reportUpdate, SINGLE_PROCESS_REPORT_TYPE);
  }

  public void updateSingleDecisionReport(final SingleDecisionReportDefinitionUpdateDto reportUpdate) {
    updateReport(reportUpdate, SINGLE_DECISION_REPORT_TYPE);
  }

  public void updateCombinedReport(final ReportDefinitionUpdateDto updatedReport) {
    updateReport(updatedReport, COMBINED_REPORT_TYPE);
  }

  private void updateReport(ReportDefinitionUpdateDto updatedReport, String elasticsearchType) {
    logger.debug("Updating report with id [{}] in Elasticsearch", updatedReport.getId());
    try {
      UpdateRequest request =
        new UpdateRequest(
          getOptimizeIndexAliasForType(elasticsearchType),
          elasticsearchType,
          updatedReport.getId()
        )
          .doc(objectMapper.writeValueAsString(updatedReport), XContentType.JSON)
          .setRefreshPolicy(IMMEDIATE)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        logger.error(
          "Was not able to update report with id [{}] and name [{}].",
          updatedReport.getId(),
          updatedReport.getName()
        );
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update report with id [%s].",
        updatedReport.getId()
      );
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (DocumentMissingException e) {
      String errorMessage = String.format(
        "Was not able to update report with id [%s] and name [%s]. Report does not exist!",
        updatedReport.getId(),
        updatedReport.getName()
      );
      logger.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void deleteSingleReport(final String reportId) {
    logger.debug("Deleting single report with id [{}]", reportId);

    DeleteByQueryRequest request = new DeleteByQueryRequest(
      getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE),
      getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE)
    )
      .setQuery(idsQuery().addIds(reportId))
      .setRefresh(true);

    BulkByScrollResponse bulkByScrollResponse;
    try {
      bulkByScrollResponse = esClient.deleteByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete single report with id [%s].", reportId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!bulkByScrollResponse.getBulkFailures().isEmpty()) {
      String message = String.format(
        "Could not delete single process report with id [%s]. Single process report does not exist."
          + "Maybe it was already deleted by someone else?",
        reportId
      );
      logger.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }

  public void removeSingleReportFromCombinedReports(final String reportId) {
    Script removeReportIdFromCombinedReportsScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.data.reportIds.removeIf(id -> id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", reportId)
    );

    NestedQueryBuilder query = nestedQuery(
      CombinedReportType.DATA,
      termQuery(CombinedReportType.DATA + "." + CombinedReportType.REPORT_IDS, reportId),
      ScoreMode.None
    );

    UpdateByQueryRequest request = new UpdateByQueryRequest(getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE))
      .setAbortOnVersionConflict(false)
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setQuery(query)
      .setScript(removeReportIdFromCombinedReportsScript)
      .setRefresh(true);

    BulkByScrollResponse bulkByScrollResponse;
    try {
      bulkByScrollResponse = esClient.updateByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not remove report with id [%s] from combined report.", reportId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!bulkByScrollResponse.getBulkFailures().isEmpty()) {
      String errorMessage =
        String.format(
          "Could not remove report id [%s] from one or more combined report/s! Error response: %s",
          reportId,
          bulkByScrollResponse.getBulkFailures()
        );
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteCombinedReport(final String reportId) {
    logger.debug("Deleting combined report with id [{}]", reportId);

    DeleteRequest request =
      new DeleteRequest(getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE), COMBINED_REPORT_TYPE, reportId)
        .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete combined report with id [%s].", reportId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message =
        String.format("Could not delete combined process report with id [%s]. " +
                        "Combined process report does not exist." +
                        "Maybe it was already deleted by someone else?", reportId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }


}
