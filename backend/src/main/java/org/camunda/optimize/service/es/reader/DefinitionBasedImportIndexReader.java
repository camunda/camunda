package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.DefinitionBasedImportIndexDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DefinitionBasedImportIndexReader {

  private final Logger logger = LoggerFactory.getLogger(DefinitionBasedImportIndexReader.class);

  @Autowired
  private Client esclient;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  public DefinitionBasedImportIndexDto getImportIndex(String typeIndexComesFrom) {
    logger.debug("Fetching definition based import index of type '{}'", typeIndexComesFrom);
    DefinitionBasedImportIndexDto dto = new DefinitionBasedImportIndexDto();
    GetResponse getResponse = null;
    try {
      getResponse = esclient
        .prepareGet(configurationService.getOptimizeIndex(), configurationService.getProcessDefinitionImportIndexType(), typeIndexComesFrom)
        .setFetchSource(true)
        .get();
    } catch (Exception ignored) {}

    if (getResponse != null && getResponse.isExists()) {
      String content = getResponse.getSourceAsString();
      try {
        dto = objectMapper.readValue(content, DefinitionBasedImportIndexDto.class);
      } catch (IOException e) {
        logger.error("Error while reading definition based import index from elastic search!", e);
      }
    } else {
      logger.debug("Was not able to retrieve definition based import index " +
        "for type '{}' from elasticsearch.", typeIndexComesFrom);
    }
    return dto;
  }

}
