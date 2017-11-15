package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
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
public class ImportIndexReader {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexReader.class);

  @Autowired
  private Client esclient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ConfigurationService configurationService;

  public Optional<AllEntitiesBasedImportIndexDto> getImportIndex(String typeIndexComesFrom) {
    logger.debug("Fetching import index of type [{}]", typeIndexComesFrom);
    GetResponse getResponse = null;
    try {
      getResponse = esclient
        .prepareGet(
          configurationService.getOptimizeIndex(),
          configurationService.getImportIndexType(),
          typeIndexComesFrom)
        .setRealtime(false)
        .get();
    } catch (Exception ignored) {}

    if (getResponse != null && getResponse.isExists()) {
      try {
        AllEntitiesBasedImportIndexDto storedIndex =
          objectMapper.readValue(getResponse.getSourceAsString(), AllEntitiesBasedImportIndexDto.class);
        return Optional.of(storedIndex);
      } catch (IOException e) {
        logger.error("Was not able to retrieve import index of [{}]. Reason: {}", typeIndexComesFrom, e);
        return Optional.empty();
      }
    } else {
      logger.debug("Was not able to retrieve import index for type '{}' from Elasticsearch. " +
        "Desired index does not exist.", typeIndexComesFrom);
      return Optional.empty();
    }
  }

}
