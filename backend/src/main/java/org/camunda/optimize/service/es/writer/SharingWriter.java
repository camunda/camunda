package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.DeleteByQueryRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
      .setSource(objectMapper.convertValue(createSharingDto, Map.class))
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
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

  public void deleteReportShares(List<String> reportShares) {
    logger.debug("Deleting report shares with ids [{}]", reportShares);

    BulkByScrollResponse response =
      DeleteByQueryAction.INSTANCE.newRequestBuilder(esclient)
        .filter(QueryBuilders.idsQuery().addIds(reportShares.toArray(new String[reportShares.size()])))
        .source(configurationService.getOptimizeIndex(configurationService.getReportShareType()))
        .refresh(true)
        .get();

    response.getDeleted();
  }

  public List<String> saveReportShares(List<ReportShareDto> toPersist) {
    List<String> result = new ArrayList<>();
    BulkRequestBuilder bulkRequest = esclient.prepareBulk();
    for (ReportShareDto share : toPersist) {
      String id = IdGenerator.getNextId();
      share.setId(id);
      result.add(id);
      bulkRequest.add(esclient
        .prepareIndex(
          configurationService.getOptimizeIndex(configurationService.getReportShareType()),
          configurationService.getReportShareType(),
          id
        )
        .setSource(objectMapper.convertValue(share, Map.class))
      );
    }
    bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    bulkRequest.get();
    return result;
  }
}
