package org.camunda.optimize.service.es.writer;

import org.camunda.optimize.service.es.schema.type.ImportIndexType;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ImportIndexWriter {

  private final Logger logger = LoggerFactory.getLogger(ImportIndexWriter.class);

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;

  public void importIndex(int importStartIndex, String typeIndexComesFrom) throws IOException {
    logger.debug("Writing import index '{}' of type '{}' to elasticsearch", importStartIndex, typeIndexComesFrom);
    esclient
      .prepareIndex(
        configurationService.getOptimizeIndex(),
        configurationService.getImportIndexType(),
        typeIndexComesFrom)
      .setSource(
        XContentFactory.jsonBuilder()
          .startObject()
            .field(ImportIndexType.IMPORT_INDEX_FIELD, importStartIndex)
          .endObject())
      .get();
  }

}
