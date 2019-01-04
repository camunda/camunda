package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CREATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_SHARE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DELETE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.REPORT_SHARE_TYPE;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@Component
public class SharingWriter {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private RestHighLevelClient esClient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;

  @Autowired
  public SharingWriter(RestHighLevelClient esClient, ConfigurationService configurationService,
                       ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  public ReportShareDto saveReportShare(ReportShareDto createSharingDto) {
    logger.debug("Writing new report share to Elasticsearch");
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    try {
      IndexRequest request = new IndexRequest(
        getOptimizeIndexAliasForType(REPORT_SHARE_TYPE),
        REPORT_SHARE_TYPE,
        id
      )
        .source(objectMapper.writeValueAsString(createSharingDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write report share to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create report share.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    logger.debug("report share with id [{}] for resource [{}] has been created", id, createSharingDto.getReportId());
    return createSharingDto;
  }

  public DashboardShareDto saveDashboardShare(DashboardShareDto createSharingDto) {
    logger.debug("Writing new dashboard share to Elasticsearch");
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    try {
      IndexRequest request = new IndexRequest(
        getOptimizeIndexAliasForType(DASHBOARD_SHARE_TYPE),
        DASHBOARD_SHARE_TYPE,
        id
      )
        .source(objectMapper.writeValueAsString(createSharingDto), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write dashboard share to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create dashboard share.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    logger.debug(
      "dashboard share with id [{}] for resource [{}] has been created",
      id,
      createSharingDto.getDashboardId()
    );
    return createSharingDto;
  }

  public void updateDashboardShare(DashboardShareDto updatedShare) {
    String id = updatedShare.getId();
    try {
      IndexRequest request = new IndexRequest(
        getOptimizeIndexAliasForType(DASHBOARD_SHARE_TYPE),
        DASHBOARD_SHARE_TYPE,
        id
      )
        .source(objectMapper.writeValueAsString(updatedShare), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write dashboard share to Elasticsearch.";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update dashboard share with id [%s] for resource [%s].",
        id,
        updatedShare.getDashboardId()
      );
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    logger.debug("dashboard share with id [{}] for resource [{}] has been updated", id, updatedShare.getDashboardId());
  }

  public void deleteReportShare(String shareId) {
    logger.debug("Deleting report share with id [{}]", shareId);
    DeleteRequest request =
      new DeleteRequest(
        getOptimizeIndexAliasForType(REPORT_SHARE_TYPE),
        REPORT_SHARE_TYPE,
        shareId
      )
        .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete report share with id [%s]. " +
                        "Maybe Optimize is not connected to Elasticsearch?", shareId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().getLowercase().equals(DELETE_SUCCESSFUL_RESPONSE_RESULT)) {
      String message =
        String.format("Could not delete report share with id [%s]. Report share does not exist." +
                        "Maybe it was already deleted by someone else?", shareId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }

  public void deleteDashboardShare(String shareId) {
    logger.debug("Deleting dashboard share with id [{}]", shareId);
    DeleteRequest request =
      new DeleteRequest(
        getOptimizeIndexAliasForType(DASHBOARD_SHARE_TYPE),
        DASHBOARD_SHARE_TYPE,
        shareId
      )
        .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete dashboard share with id [%s]. " +
                        "Maybe Optimize is not connected to Elasticsearch?", shareId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().getLowercase().equals(DELETE_SUCCESSFUL_RESPONSE_RESULT)) {
      String message =
        String.format("Could not delete dashboard share with id [%s]. Dashboard share does not exist." +
                        "Maybe it was already deleted by someone else?", shareId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }

}
