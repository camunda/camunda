package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.schema.type.CombinedReportType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequestBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.time.OffsetDateTime;
import java.util.Collections;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CREATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DELETE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ReportWriter {
  private static final Logger logger = LoggerFactory.getLogger(ReportWriter.class);

  private static final String DEFAULT_REPORT_NAME = "New Report";


  private final Client esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Autowired
  public ReportWriter(final Client esClient,
                      final ConfigurationService configurationService,
                      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  public IdDto createNewCombinedReportAndReturnId(final String userId) {
    final CombinedReportDefinitionDto reportDefinitionDto = new CombinedReportDefinitionDto();
    return createNewCombinedReportAndReturnId(userId, reportDefinitionDto);
  }

  private IdDto createNewCombinedReportAndReturnId(final String userId,
                                                   final CombinedReportDefinitionDto reportDefinitionDto) {
    logger.debug("Writing new combined report to Elasticsearch");
    final String id = IdGenerator.getNextId();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = OffsetDateTime.now();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(DEFAULT_REPORT_NAME);

    try {
      IndexResponse indexResponse = esClient
        .prepareIndex(
          getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE),
          COMBINED_REPORT_TYPE,
          id
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
        .get();

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write report to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }

      logger.debug("Report with id [{}] has successfully been created.", id);
      IdDto idDto = new IdDto();
      idDto.setId(id);
      return idDto;
    } catch (JsonProcessingException e) {
      String errorMessage = "Was not able to insert combined report. Could not serialize report!";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public IdDto createNewSingleReportAndReturnId(final String userId,
                                                final SingleReportDefinitionDto reportDefinitionDto) {
    logger.debug("Writing new single report to Elasticsearch");

    final String id = IdGenerator.getNextId();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = OffsetDateTime.now();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(DEFAULT_REPORT_NAME);
    switch (reportDefinitionDto.getReportType()) {
      case PROCESS:
        reportDefinitionDto.setData(new ProcessReportDataDto());
        break;
      case DECISION:
        reportDefinitionDto.setData(new DecisionReportDataDto());
        break;
      default:
        throw new IllegalStateException("Unsupported type: " + reportDefinitionDto.getReportType());
    }

    try {
      final String optimizeIndexTypeForReportType =
        getOptimizeIndexTypeForReportType(reportDefinitionDto.getReportType());
      IndexResponse indexResponse = esClient
        .prepareIndex(getOptimizeIndexAliasForType(optimizeIndexTypeForReportType), optimizeIndexTypeForReportType, id)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
        .get();

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write report to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }

      logger.debug("Single Report with id [{}] has successfully been created.", id);
      IdDto idDto = new IdDto();
      idDto.setId(id);
      return idDto;
    } catch (JsonProcessingException e) {
      String errorMessage = "Was not able to insert single report. Could not serialize report!";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public static String getOptimizeIndexTypeForReportType(final ReportType type) {
    switch (type) {
      case PROCESS:
        return SINGLE_PROCESS_REPORT_TYPE;
      case DECISION:
        return SINGLE_DECISION_REPORT_TYPE;
      default:
        throw new IllegalStateException("Unsupported reportType: " + type);
    }
  }

  public void updateSingleReport(final ReportDefinitionUpdateDto updatedReport) {
    if (updatedReport.getData() instanceof  ProcessReportDataDto) {
      updateReport(updatedReport, SINGLE_PROCESS_REPORT_TYPE);
    } else if (updatedReport.getData() instanceof DecisionReportDataDto) {
      updateReport(updatedReport, SINGLE_DECISION_REPORT_TYPE);
    }
  }

  public void updateCombinedReport(final ReportDefinitionUpdateDto updatedReport) {
    updateReport(updatedReport, COMBINED_REPORT_TYPE);
  }

  public void updateReport(ReportDefinitionUpdateDto updatedReport, String elasticsearchType) {
    logger.debug("Updating report with id [{}] in Elasticsearch", updatedReport.getId());
    try {
      UpdateResponse updateResponse = esClient
        .prepareUpdate(
          getOptimizeIndexAliasForType(elasticsearchType),
          elasticsearchType,
          updatedReport.getId()
        )
        .setDoc(objectMapper.writeValueAsString(updatedReport), XContentType.JSON)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
        .get();
      if (updateResponse.getShardInfo().getFailed() > 0) {
        logger.error(
          "Was not able to update report with id [{}] and name [{}].",
          updatedReport.getId(),
          updatedReport.getName()
        );
        throw new OptimizeRuntimeException("Was not able to update collection!");
      }
    } catch (JsonProcessingException e) {
      String errorMessage = String.format(
        "Was not able to update report with id [%s]. Could not serialize report update!",
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

    final BulkByScrollResponse response = DeleteByQueryAction.INSTANCE.newRequestBuilder(esClient)
      .source(
        getOptimizeIndexAliasForType(SINGLE_PROCESS_REPORT_TYPE),
        getOptimizeIndexAliasForType(SINGLE_DECISION_REPORT_TYPE)
      )
      .filter(idsQuery().addIds(reportId))
      .refresh(true)
      .get();

    if (!response.getBulkFailures().isEmpty()) {
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
    UpdateByQueryRequestBuilder updateByQuery = UpdateByQueryAction.INSTANCE.newRequestBuilder(esClient);
    Script removeReportIdFromCombinedReportsScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.data.reportIds.removeIf(id -> id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", reportId)
    );

    updateByQuery.source(getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE))
      .abortOnVersionConflict(false)
      .setMaxRetries(configurationService.getNumberOfRetriesOnConflict())
      .filter(nestedQuery(
        CombinedReportType.DATA,
        termQuery(CombinedReportType.DATA + "." + CombinedReportType.REPORT_IDS, reportId),
        ScoreMode.None
      ))
      .script(removeReportIdFromCombinedReportsScript)
      .refresh(true);

    BulkByScrollResponse response = updateByQuery.get();
    if (!response.getBulkFailures().isEmpty()) {
      String errorMessage =
        String.format(
          "Could not remove report id [%s] from one or more combined report/s! Error response: %s",
          reportId,
          response.getBulkFailures()
        );
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteCombinedReport(final String reportId) {
    logger.debug("Deleting combined report with id [{}]", reportId);

    DeleteResponse deleteResponse = esClient.prepareDelete(
      getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE),
      COMBINED_REPORT_TYPE,
      reportId
    )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();

    if (!deleteResponse.getResult().getLowercase().equals(DELETE_SUCCESSFUL_RESPONSE_RESULT)) {
      String message =
        String.format("Could not delete combined process report with id [%s]. " +
                        "Combined process report does not exist." +
                        "Maybe it was already deleted by someone else?", reportId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }


}
