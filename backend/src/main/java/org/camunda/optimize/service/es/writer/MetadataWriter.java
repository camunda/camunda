package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;


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
      String source = objectMapper.writeValueAsString(metadataDto);
      esclient
        .prepareIndex(
          getOptimizeIndexAliasForType(configurationService.getMetaDataType()),
          configurationService.getMetaDataType(),
          ID
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setSource(source, XContentType.JSON)
        .get();
    } catch (JsonProcessingException e) {
      logger.error("can't write metadata", e);
    }
  }
}
