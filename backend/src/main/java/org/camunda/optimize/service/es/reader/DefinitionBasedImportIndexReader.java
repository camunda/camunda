package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.dto.optimize.importing.DefinitionBasedImportIndexDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.DefinitionImportIndexType.ALREADY_IMPORTED_PROCESS_DEFINITIONS;
import static org.camunda.optimize.service.es.schema.type.DefinitionImportIndexType.CURRENT_PROCESS_DEFINITION;
import static org.camunda.optimize.service.es.schema.type.DefinitionImportIndexType.IMPORT_INDEX_FIELD;
import static org.camunda.optimize.service.es.schema.type.DefinitionImportIndexType.TOTAL_ENTITIES_IMPORTED;

@Component
public class DefinitionBasedImportIndexReader {

  private final Logger logger = LoggerFactory.getLogger(DefinitionBasedImportIndexReader.class);

  @Autowired
  private Client esclient;

  @Autowired
  private ConfigurationService configurationService;

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
      Map<String, Object> sourceMap = getResponse.getSource();
      dto.setTotalEntitiesImported((int) sourceMap.get(TOTAL_ENTITIES_IMPORTED));
      dto.setImportIndex((int) sourceMap.get(IMPORT_INDEX_FIELD));
      dto.setCurrentProcessDefinition((String) sourceMap.get(CURRENT_PROCESS_DEFINITION));
      dto.setAlreadyImportedProcessDefinitions((List<String>) sourceMap.get(ALREADY_IMPORTED_PROCESS_DEFINITIONS));
    } else {
      logger.debug("Was not able to retrieve definition based import index " +
        "for type '{}' from elasticsearch.", typeIndexComesFrom);
    }
    return dto;
  }

}
