package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.service.es.schema.type.DashboardType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
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

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.DashboardType.CREATED;
import static org.camunda.optimize.service.es.schema.type.DashboardType.ID;
import static org.camunda.optimize.service.es.schema.type.DashboardType.LAST_MODIFIED;
import static org.camunda.optimize.service.es.schema.type.DashboardType.LAST_MODIFIER;
import static org.camunda.optimize.service.es.schema.type.DashboardType.NAME;
import static org.camunda.optimize.service.es.schema.type.DashboardType.OWNER;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;

@Component
public class DashboardWriter {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  private static final String DEFAULT_DASHBOARD_NAME = "New Dashboard";

  public IdDto createNewDashboardAndReturnId(String userId) {
    logger.debug("Writing new dashboard to Elasticsearch");

    String id = IdGenerator.getNextId();
    Map<String, Object> map = new HashMap<>();
    map.put(CREATED, currentDateAsString());
    map.put(LAST_MODIFIED, currentDateAsString());
    map.put(OWNER, userId);
    map.put(LAST_MODIFIER, userId);
    map.put(NAME, DEFAULT_DASHBOARD_NAME);
    map.put(ID, id);

    esclient
      .prepareIndex(getOptimizeIndexAliasForType(DASHBOARD_TYPE), DASHBOARD_TYPE, id)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(map)
      .get();

    logger.debug("Dashboard with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateDashboard(DashboardDefinitionUpdateDto dashboard, String id) throws
                                                                                 OptimizeException,
                                                                                 JsonProcessingException {
    logger.debug("Updating dashboard with id [{}] in Elasticsearch", id);
    UpdateResponse updateResponse = esclient
      .prepareUpdate(getOptimizeIndexAliasForType(DASHBOARD_TYPE), DASHBOARD_TYPE, id)
      .setDoc(objectMapper.writeValueAsString(dashboard), XContentType.JSON)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setRetryOnConflict(configurationService.getNumberOfRetriesOnConflict())
      .get();

    if (updateResponse.getShardInfo().getFailed() > 0) {
      logger.error(
        "Was not able to store dashboard with id [{}] and name [{}]. Exception: {} \n Stacktrace: {}",
        id,
        dashboard.getName()
      );
      throw new OptimizeException("Was not able to store dashboard!");
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
      logger.error("Could not remove report id from one or more dashboard/s! {}", response.getBulkFailures());
    }
  }

  public void deleteDashboard(String dashboardId) {
    logger.debug("Deleting dashboard with id [{}]", dashboardId);
    esclient.prepareDelete(getOptimizeIndexAliasForType(DASHBOARD_TYPE), DASHBOARD_TYPE, dashboardId)
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();
  }

  private String currentDateAsString() {
    return dateTimeFormatter.format(LocalDateUtil.getCurrentDateTime());
  }
}
