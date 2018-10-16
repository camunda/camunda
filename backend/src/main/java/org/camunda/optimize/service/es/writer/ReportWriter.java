package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.service.es.schema.type.CombinedReportType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
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

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.SingleReportType.CREATED;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.ID;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.LAST_MODIFIED;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.LAST_MODIFIER;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.NAME;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.OWNER;
import static org.camunda.optimize.service.es.schema.type.SingleReportType.REPORT_TYPE;

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

    return createNewReportAndReturnId(
      userId,
      ReportConstants.SINGLE_REPORT_TYPE,
      ElasticsearchConstants.SINGLE_REPORT_TYPE
    );
  }

  public IdDto createNewCombinedReportAndReturnId(String userId) {
    logger.debug("Creating combined report!");

    return createNewReportAndReturnId(
      userId,
      ReportConstants.COMBINED_REPORT_TYPE,
      ElasticsearchConstants.COMBINED_REPORT_TYPE
    );
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

    esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(elasticsearchType),
        elasticsearchType,
        id
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(map)
      .get();

    logger.debug("Report with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateSingleReport(ReportDefinitionUpdateDto updatedReport) throws OptimizeException, JsonProcessingException {
    updateReport(updatedReport, ElasticsearchConstants.SINGLE_REPORT_TYPE);
  }

  public void updateCombinedReport(ReportDefinitionUpdateDto updatedReport) throws OptimizeException, JsonProcessingException {
    updateReport(updatedReport, ElasticsearchConstants.COMBINED_REPORT_TYPE);
  }

  public void updateReport(ReportDefinitionUpdateDto updatedReport, String elasticsearchType)
    throws OptimizeException, JsonProcessingException {

    logger.debug("Updating report with id [{}] in Elasticsearch", updatedReport.getId());
    UpdateResponse updateResponse = esclient
      .prepareUpdate(
        configurationService.getOptimizeIndex(elasticsearchType),
        elasticsearchType,
        updatedReport.getId()
      )
      .setDoc(objectMapper.writeValueAsString(updatedReport), XContentType.JSON)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
      .get();

    if (updateResponse.getShardInfo().getFailed() > 0) {
      logger.error("Was not able to store report with id [{}] and name [{}]. Exception: {} \n Stacktrace: {}",
        updatedReport.getId(),
        updatedReport.getName());
      throw new OptimizeException("Was not able to store report!");
    }
  }

  private String currentDateAsString() {
    return dateTimeFormatter.format(LocalDateUtil.getCurrentDateTime());
  }

  public void deleteSingleReport(String reportId) {
    logger.debug("Deleting single report with id [{}]", reportId);

    esclient.prepareDelete(
      configurationService.getOptimizeIndex(ElasticsearchConstants.SINGLE_REPORT_TYPE),
      ElasticsearchConstants.SINGLE_REPORT_TYPE,
      reportId
    )
    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
    .get();
  }

  public void removeSingleReportFromCombinedReports(String reportId) {
    UpdateByQueryRequestBuilder updateByQuery = UpdateByQueryAction.INSTANCE.newRequestBuilder(esclient);
    Script removeReportIdFromCombinedReportsScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.data.reportIds.removeIf(id -> id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", reportId)
    );

    updateByQuery.source(configurationService.getOptimizeIndex(ElasticsearchConstants.COMBINED_REPORT_TYPE))
      .abortOnVersionConflict(false)
      .setMaxRetries(configurationService.getNumberOfRetriesOnConflict())
      .filter(
        QueryBuilders.nestedQuery(
          CombinedReportType.DATA,
          QueryBuilders.termQuery(CombinedReportType.DATA + "." + CombinedReportType.REPORT_IDS, reportId),
          ScoreMode.None)
      )
      .script(removeReportIdFromCombinedReportsScript)
      .refresh(true);

    BulkByScrollResponse response = updateByQuery.get();
    if(!response.getBulkFailures().isEmpty()) {
      logger.error("Could not remove report id from one or more combined report/s! {}", response.getBulkFailures());
    }
  }

  public void deleteCombinedReport(String reportId) {
    logger.debug("Deleting combined report with id [{}]", reportId);

    esclient.prepareDelete(
      configurationService.getOptimizeIndex(ElasticsearchConstants.COMBINED_REPORT_TYPE),
      ElasticsearchConstants.COMBINED_REPORT_TYPE,
      reportId
    )
    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
    .get();
  }


}
