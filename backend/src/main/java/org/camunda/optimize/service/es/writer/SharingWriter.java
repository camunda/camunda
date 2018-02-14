package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Askar Akhmerov
 */

@Component
public class SharingWriter {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public ReportShareDto saveReportShare(ReportShareDto createSharingDto) {
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(configurationService.getReportShareType()),
        configurationService.getReportShareType(),
        id
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(objectMapper.convertValue(createSharingDto, Map.class))
      .get();

    logger.debug("report share with id [{}] for resource [{}] has been created", id, createSharingDto.getReportId());
    return createSharingDto;
  }

  public DashboardShareDto saveDashboardShare(DashboardShareDto createSharingDto) {
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(configurationService.getDashboardShareType()),
        configurationService.getDashboardShareType(),
        id
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(objectMapper.convertValue(createSharingDto, Map.class))
      .get();

    logger.debug("dashboard share with id [{}] for resource [{}] has been created", id, createSharingDto.getDashboardId());
    return createSharingDto;
  }

  public DashboardShareDto updateDashboardShare(DashboardShareDto updatedShare) {
    String id = updatedShare.getId();
    esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(configurationService.getDashboardShareType()),
        configurationService.getDashboardShareType(),
        id
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(objectMapper.convertValue(updatedShare, Map.class))
      .get();

    logger.debug("dashboard share with id [{}] for resource [{}] has been created", id, updatedShare.getDashboardId());
    return updatedShare;
  }

  public void deleteReportShare(String shareId) {
    logger.debug("Deleting report share with id [{}]", shareId);
    esclient.prepareDelete(
      configurationService.getOptimizeIndex(configurationService.getReportShareType()),
      configurationService.getReportShareType(),
      shareId
    )
    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
    .get();
  }

  public void deleteDashboardShare(String shareId) {
    logger.debug("Deleting dashboard share with id [{}]", shareId);
    esclient.prepareDelete(
      configurationService.getOptimizeIndex(configurationService.getDashboardShareType()),
      configurationService.getDashboardShareType(),
      shareId
    )
    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
    .get();
  }
}
