package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Component
public class MetadataWriter {
  public static final String ID = "1";
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  public void writeMetadata(MetadataDto metadataDto) {
    try {
      Map valueMap = objectMapper.readValue
        (objectMapper.writeValueAsString(metadataDto), HashMap.class);
      IndexResponse response = esclient
        .prepareIndex(
          configurationService.getOptimizeIndex(configurationService.getMeataDataType()),
          configurationService.getMeataDataType(),
          ID
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(valueMap)
        .get();
    } catch (JsonProcessingException e) {
      logger.error("can't write metadata", e);
    } catch (IOException e) {
      logger.error("can't write metadata", e);
    }
  }
}
