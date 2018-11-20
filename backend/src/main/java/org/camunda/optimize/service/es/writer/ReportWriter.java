package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.schema.type.CombinedReportType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
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

  public IdDto createNewCombinedReportAndReturnId(String userId) throws JsonProcessingException {
    final CombinedReportDefinitionDto reportDefinitionDto = new CombinedReportDefinitionDto();
    return createNewCombinedReportAndReturnId(userId, reportDefinitionDto);
  }

  public IdDto createNewCombinedReportAndReturnId(final String userId,
                                                  final CombinedReportDefinitionDto reportDefinitionDto)
    throws JsonProcessingException {
    logger.debug("Writing new combined report to Elasticsearch");
    final String id = IdGenerator.getNextId();
    reportDefinitionDto.setId(id);
    final OffsetDateTime now = OffsetDateTime.now();
    reportDefinitionDto.setCreated(now);
    reportDefinitionDto.setLastModified(now);
    reportDefinitionDto.setOwner(userId);
    reportDefinitionDto.setLastModifier(userId);
    reportDefinitionDto.setName(DEFAULT_REPORT_NAME);

    esclient
      .prepareIndex(getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE), COMBINED_REPORT_TYPE, id)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
      .get();

    logger.debug("Report with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public IdDto createNewSingleReportAndReturnId(final String userId,
                                                final SingleReportDefinitionDto reportDefinitionDto)
    throws JsonProcessingException {
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
      default:
        throw new IllegalStateException("Unsupported type: " + reportDefinitionDto.getReportType());
    }
    esclient
      .prepareIndex(getOptimizeIndexAliasForType(SINGLE_REPORT_TYPE), SINGLE_REPORT_TYPE, id)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(objectMapper.writeValueAsString(reportDefinitionDto), XContentType.JSON)
      .get();

    logger.debug("Single Report with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateSingleReport(ReportDefinitionUpdateDto updatedReport)
    throws OptimizeException, JsonProcessingException {
    updateReport(updatedReport, SINGLE_REPORT_TYPE);
  }

  public void updateCombinedReport(ReportDefinitionUpdateDto updatedReport)
    throws OptimizeException, JsonProcessingException {
    updateReport(updatedReport, COMBINED_REPORT_TYPE);
  }

  public void updateReport(ReportDefinitionUpdateDto updatedReport, String elasticsearchType)
    throws OptimizeException, JsonProcessingException {

    logger.debug("Updating report with id [{}] in Elasticsearch", updatedReport.getId());
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
        "Was not able to store report with id [{}] and name [{}]. Exception: {} \n Stacktrace: {}",
        updatedReport.getId(),
        updatedReport.getName()
      );
      throw new OptimizeException("Was not able to store report!");
    }
  }

  public void deleteSingleReport(String reportId) {
    logger.debug("Deleting single report with id [{}]", reportId);

    esclient.prepareDelete(getOptimizeIndexAliasForType(SINGLE_REPORT_TYPE), SINGLE_REPORT_TYPE, reportId)
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

    updateByQuery.source(getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE))
      .abortOnVersionConflict(false)
      .setMaxRetries(configurationService.getNumberOfRetriesOnConflict())
      .filter(QueryBuilders.nestedQuery(
        CombinedReportType.DATA,
        QueryBuilders.termQuery(CombinedReportType.DATA + "." + CombinedReportType.REPORT_IDS, reportId),
        ScoreMode.None
      ))
      .script(removeReportIdFromCombinedReportsScript)
      .refresh(true);

    BulkByScrollResponse response = updateByQuery.get();
    if (!response.getBulkFailures().isEmpty()) {
      logger.error("Could not remove report id from one or more combined report/s! {}", response.getBulkFailures());
    }
  }

  public void deleteCombinedReport(String reportId) {
    logger.debug("Deleting combined report with id [{}]", reportId);

    esclient.prepareDelete(getOptimizeIndexAliasForType(COMBINED_REPORT_TYPE), COMBINED_REPORT_TYPE, reportId)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();
  }


}
