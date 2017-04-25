package org.camunda.optimize.service.es.reader;

import org.camunda.optimize.service.es.schema.type.ImportIndexType;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImportIndexReader {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexReader.class);

  @Autowired
  private TransportClient esclient;

  @Autowired
  private ConfigurationService configurationService;

  public int getImportIndex(String typeIndexComesFrom) {
    logger.debug("Fetching import index of type '" + typeIndexComesFrom + "'");
    GetResponse getResponse = null;
    try {
      getResponse = esclient
        .prepareGet(configurationService.getOptimizeIndex(), configurationService.getImportIndexType(), typeIndexComesFrom)
        .setRealtime(false)
        .get();
    } catch (Exception ignored) {}

    if (getResponse != null && getResponse.isExists()) {
      return (Integer) getResponse.getSource().get(ImportIndexType.IMPORT_INDEX_FIELD);
    } else {
      logger.debug("Was not able to retrieve import index for type '" + typeIndexComesFrom + "' from elasticsearch.");
      return 0;
    }
  }

}
