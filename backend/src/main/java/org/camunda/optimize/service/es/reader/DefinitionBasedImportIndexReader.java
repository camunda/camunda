package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.index.DefinitionBasedImportIndexDto;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class DefinitionBasedImportIndexReader {

  private final Logger logger = LoggerFactory.getLogger(DefinitionBasedImportIndexReader.class);

  @Autowired
  private Client esclient;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper objectMapper;

  public Optional<DefinitionBasedImportIndexDto> getImportIndex(String typeIndexComesFrom, String engineAlias) {
    logger.debug("Fetching definition based import index of type '{}'", typeIndexComesFrom);
    DefinitionBasedImportIndexDto dto;
    GetResponse getResponse = null;
    try {
      getResponse = esclient
        .prepareGet(
          configurationService.getOptimizeIndex(),
          configurationService.getProcessDefinitionImportIndexType(),
          EsHelper.constructKey(typeIndexComesFrom, engineAlias))
        .setFetchSource(true)
        .get();
    } catch (Exception ignored) {}

    if (getResponse != null && getResponse.isExists()) {
      String content = getResponse.getSourceAsString();
      try {
        dto = objectMapper.readValue(content, DefinitionBasedImportIndexDto.class);
      } catch (IOException e) {
        logger.debug("Error while reading definition based import index from elastic search!", e);
        return Optional.empty();
      }
    } else {
      logger.debug("Was not able to retrieve definition based import index " +
        "for type [{}] from elasticsearch.", typeIndexComesFrom);
      return Optional.empty();
    }
    return Optional.of(dto);
  }

}
