package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
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

  public SharingDto saveShare(SharingDto createSharingDto) {
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);
    esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(configurationService.getShareType()),
        configurationService.getShareType(),
        id
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .setSource(objectMapper.convertValue(createSharingDto, Map.class))
      .get();

    logger.debug("share with id [{}] for resource [{}] has been created", id, createSharingDto.getResourceId());
    return createSharingDto;
  }

  public void deleteShare(String shareId) {
    logger.debug("Deleting share with id [{}]", shareId);
    esclient.prepareDelete(
      configurationService.getOptimizeIndex(configurationService.getShareType()),
      configurationService.getShareType(),
      shareId
    )
    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
    .get();
  }
}
