package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.service.es.schema.type.DashboardType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
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
import java.util.Collections;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CREATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DELETE_SUCCESSFUL_RESPONSE_RESULT;

@Component
public class DashboardWriter {

  private static final String DEFAULT_DASHBOARD_NAME = "New Dashboard";
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private TransportClient esclient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;

  @Autowired
  public DashboardWriter(TransportClient esclient, ConfigurationService configurationService, ObjectMapper objectMapper) {
    this.esclient = esclient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  public IdDto createNewDashboardAndReturnId(String userId) {
    logger.debug("Writing new dashboard to Elasticsearch");

    String id = IdGenerator.getNextId();
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setCreated(LocalDateUtil.getCurrentDateTime());
    dashboard.setLastModified(LocalDateUtil.getCurrentDateTime());
    dashboard.setOwner(userId);
    dashboard.setLastModifier(userId);
    dashboard.setName(DEFAULT_DASHBOARD_NAME);
    dashboard.setId(id);

    try {
      IndexResponse indexResponse = esclient
        .prepareIndex(getOptimizeIndexAliasForType(DASHBOARD_TYPE), DASHBOARD_TYPE, id)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(objectMapper.writeValueAsString(dashboard), XContentType.JSON)
        .get();

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write dashboard to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (JsonProcessingException e) {
      String errorMessage = "Could not create dashboard.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    logger.debug("Dashboard with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateDashboard(DashboardDefinitionUpdateDto dashboard, String id) {
    logger.debug("Updating dashboard with id [{}] in Elasticsearch", id);
    try {
      UpdateResponse updateResponse = esclient
        .prepareUpdate(getOptimizeIndexAliasForType(DASHBOARD_TYPE), DASHBOARD_TYPE, id)
        .setDoc(objectMapper.writeValueAsString(dashboard), XContentType.JSON)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
        .get();

      if (updateResponse.getShardInfo().getFailed() > 0) {
        logger.error(
          "Was not able to update dashboard with id [{}] and name [{}].",
          id,
          dashboard.getName()
        );
        throw new OptimizeRuntimeException("Was not able to update dashboard!");
      }
    } catch (JsonProcessingException e) {
      String errorMessage = String.format(
        "Was not able to update dashboard with id [%s] and name [%s]. Could not serialize dashboard update!",
        id,
        dashboard.getName()
      );
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (DocumentMissingException e) {
      String errorMessage = String.format(
        "Was not able to update dashboard with id [%s] and name [%s]. Dashboard does not exist!",
        id,
        dashboard.getName()
      );
      logger.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void removeReportFromDashboards(String reportId) {
    UpdateByQueryRequestBuilder updateByQuery = UpdateByQueryAction.INSTANCE.newRequestBuilder(esclient);
    Script removeReportIdFromCombinedReportsScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.reports.removeIf(report -> report.id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", reportId)
    );

    updateByQuery.source(getOptimizeIndexAliasForType(DASHBOARD_TYPE))
      .abortOnVersionConflict(false)
      .setMaxRetries(configurationService.getNumberOfRetriesOnConflict())
      .filter(
        QueryBuilders.nestedQuery(
          DashboardType.REPORTS,
          QueryBuilders.termQuery(DashboardType.REPORTS + "." + DashboardType.ID, reportId),
          ScoreMode.None
        )
      )
      .script(removeReportIdFromCombinedReportsScript)
      .refresh(true);

    BulkByScrollResponse response = updateByQuery.get();
    if (!response.getBulkFailures().isEmpty()) {
      String errorMessage =
        String.format(
          "Could not remove report id [%s] from dashboard! Error response: %s",
          reportId,
          response.getBulkFailures()
        );
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteDashboard(String dashboardId) {
    logger.debug("Deleting dashboard with id [{}]", dashboardId);
    DeleteResponse deleteResponse = esclient.prepareDelete(
      getOptimizeIndexAliasForType(DASHBOARD_TYPE),
      DASHBOARD_TYPE,
      dashboardId
    )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();

    if (!deleteResponse.getResult().getLowercase().equals(DELETE_SUCCESSFUL_RESPONSE_RESULT)) {
      String message =
        String.format("Could not delete dashboard with id [%s]. Dashboard does not exist." +
                        "Maybe it was already deleted by someone else?", dashboardId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }
}
