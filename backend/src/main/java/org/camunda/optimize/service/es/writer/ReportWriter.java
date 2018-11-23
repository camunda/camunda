package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.es.schema.type.CombinedReportType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryRequestBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.CREATED;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.ID;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.LAST_MODIFIED;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.LAST_MODIFIER;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.NAME;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.OWNER;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CREATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DELETE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_REPORT_TYPE;

@Component
public class ReportWriter {

  private static final String DEFAULT_REPORT_NAME = "New Report";

  private final Logger logger = LoggerFactory.getLogger(getClass());
  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public IdDto createNewSingleReportAndReturnId(String userId) {
    logger.debug("Creating single report!");

    return createNewReportAndReturnId(userId, ReportConstants.SINGLE_REPORT_TYPE, SINGLE_REPORT_TYPE);
  }

  public IdDto createNewCombinedReportAndReturnId(String userId) {
    logger.debug("Creating combined report!");

    return createNewReportAndReturnId(userId, ReportConstants.COMBINED_REPORT_TYPE, COMBINED_REPORT_TYPE);
  }

  private IdDto createNewReportAndReturnId(String userId, String reportType, String elasticsearchType) {
    logger.debug("Writing new report to Elasticsearch");

    String id = IdGenerator.getNextId();
    Map<String, Object> map = new HashMap<>();
    map.put(CREATED, currentDateAsString());
    map.put(LAST_MODIFIED, currentDateAsString());
    map.put(OWNER, userId);
    map.put(LAST_MODIFIER, userId);
    map.put(NAME, DEFAULT_REPORT_NAME);
    map.put(REPORT_TYPE, reportType);
    map.put(ID, id);

    IndexResponse indexResponse = esclient
      .prepareIndex(
        getOptimizeIndexAliasForType(elasticsearchType),
        elasticsearchType,
        id
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(map)
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
  }

  public void updateSingleReport(ReportDefinitionUpdateDto updatedReport) {
    updateReport(updatedReport, SINGLE_REPORT_TYPE);
  }

  public void updateCombinedReport(ReportDefinitionUpdateDto updatedReport) {
    updateReport(updatedReport, COMBINED_REPORT_TYPE);
  }

  public void updateReport(ReportDefinitionUpdateDto updatedReport, String elasticsearchType) {
    logger.debug("Updating report with id [{}] in Elasticsearch", updatedReport.getId());
    try {
      UpdateResponse updateResponse = esclient
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

  private String currentDateAsString() {
    return dateTimeFormatter.format(LocalDateUtil.getCurrentDateTime());
  }

  public void deleteSingleReport(String reportId) {
    logger.debug("Deleting single report with id [{}]", reportId);

    DeleteResponse deleteResponse = esclient.prepareDelete(
      getOptimizeIndexAliasForType(SINGLE_REPORT_TYPE),
      SINGLE_REPORT_TYPE,
      reportId
    )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();

    if (!deleteResponse.getResult().getLowercase().equals(DELETE_SUCCESSFUL_RESPONSE_RESULT)) {
      String message =
        String.format("Could not delete single process report with id [%s]. Single process report does not exist." +
                        "Maybe it was already deleted by someone else?", reportId);
      logger.error(message);
      throw new OptimizeRuntimeException(message);
    }
  }

  public void removeSingleReportFromCombinedReports(String reportId) {
    UpdateByQueryRequestBuilder updateByQuery = UpdateByQueryAction.INSTANCE.newRequestBuilder(esclient);
    Script removeReportIdFromCombinedReportsScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.data.reportIds.removeIf(id -> id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", reportId)
    );

    updateByQuery.source(getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE))
      .abortOnVersionConflict(false)
      .setMaxRetries(configurationService.getNumberOfRetriesOnConflict())
      .filter(
        QueryBuilders.nestedQuery(
          CombinedReportType.DATA,
          QueryBuilders.termQuery(CombinedReportType.DATA + "." + CombinedReportType.REPORT_IDS, reportId),
          ScoreMode.None
        )
      )
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

  public void deleteCombinedReport(String reportId) {
    logger.debug("Deleting combined report with id [{}]", reportId);

    DeleteResponse deleteResponse = esclient.prepareDelete(
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
