package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.CREATE_SUCCESSFUL_RESPONSE_RESULT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DELETE_SUCCESSFUL_RESPONSE_RESULT;

@Component
public class SharingWriter {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public ReportShareDto saveReportShare(ReportShareDto createSharingDto) {
    logger.debug("Writing new report share to Elasticsearch");
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    try {
      IndexResponse indexResponse = esclient
        .prepareIndex(
          getOptimizeIndexAliasForType(ElasticsearchConstants.REPORT_SHARE_TYPE),
          ElasticsearchConstants.REPORT_SHARE_TYPE,
          id
        )
        .setSource(objectMapper.writeValueAsString(createSharingDto), XContentType.JSON)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .get();

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write report share to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (JsonProcessingException e) {
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
      IndexResponse indexResponse = esclient
        .prepareIndex(
          getOptimizeIndexAliasForType(ElasticsearchConstants.DASHBOARD_SHARE_TYPE),
          ElasticsearchConstants.DASHBOARD_SHARE_TYPE,
          id
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(objectMapper.writeValueAsString(createSharingDto), XContentType.JSON)
        .get();

      if (!indexResponse.getResult().getLowercase().equals(CREATE_SUCCESSFUL_RESPONSE_RESULT)) {
        String message = "Could not write dashboard share to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (JsonProcessingException e) {
      String errorMessage = "Could not create dashboard share.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    logger.debug("dashboard share with id [{}] for resource [{}] has been created", id, createSharingDto.getDashboardId());
    return createSharingDto;
  }

  public void updateDashboardShare(DashboardShareDto updatedShare) {
    String id = updatedShare.getId();
    try {
      esclient
        .prepareIndex(
          getOptimizeIndexAliasForType(ElasticsearchConstants.DASHBOARD_SHARE_TYPE),
          ElasticsearchConstants.DASHBOARD_SHARE_TYPE,
          id
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(objectMapper.writeValueAsString(updatedShare), XContentType.JSON)
        .get();
    } catch (JsonProcessingException e) {
      String errorMessage = String.format(
        "Was not able to update dashboard share with id [%s] for resource [%s]. " +
          "Could not serialize dashboard share update!",
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
    DeleteResponse deleteResponse = esclient.prepareDelete(
      getOptimizeIndexAliasForType(ElasticsearchConstants.REPORT_SHARE_TYPE),
      ElasticsearchConstants.REPORT_SHARE_TYPE,
      shareId
    )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();

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
    DeleteResponse deleteResponse = esclient.prepareDelete(
      getOptimizeIndexAliasForType(ElasticsearchConstants.DASHBOARD_SHARE_TYPE),
      ElasticsearchConstants.DASHBOARD_SHARE_TYPE,
      shareId
    )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();

    if (!deleteResponse.getResult().getLowercase().equals(DELETE_SUCCESSFUL_RESPONSE_RESULT)) {
      String message =
        String.format("Could not delete dashboard share with id [%s]. Dashboard share does not exist." +
                        "Maybe it was already deleted by someone else?", shareId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }

}
