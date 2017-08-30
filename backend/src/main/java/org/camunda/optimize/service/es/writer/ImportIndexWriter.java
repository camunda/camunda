package org.camunda.optimize.service.es.writer;

import org.camunda.optimize.service.es.schema.type.ImportIndexType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@Component
public class ImportIndexWriter {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexWriter.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;

  public void importIndex(int importStartIndex, String typeIndexComesFrom, String engine) throws IOException {
    logger.debug("Writing import index '{}' of type '{}' to elasticsearch", importStartIndex, typeIndexComesFrom);
    esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(),
        configurationService.getImportIndexType(),
        typeIndexComesFrom)
      .setSource(
        XContentFactory.jsonBuilder()
          .startObject()
            .field(ImportIndexType.ENGINE, engine)
            .field(ImportIndexType.IMPORT_INDEX_FIELD, importStartIndex)
          .endObject()
      )
      .setRefreshPolicy(IMMEDIATE)
      .get();
  }

}
