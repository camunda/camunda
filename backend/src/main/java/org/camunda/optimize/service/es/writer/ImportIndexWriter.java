package org.camunda.optimize.service.es.writer;

import org.camunda.optimize.service.es.schema.type.ImportIndexType;
import org.camunda.optimize.service.util.ConfigurationService;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ImportIndexWriter {

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;

  public void importIndex(int importStartIndex, String typeIndexComesFrom) throws IOException {
    esclient
      .prepareIndex(configurationService.getOptimizeIndex(), configurationService.getImportIndexType(), typeIndexComesFrom)
      .setSource(XContentFactory.jsonBuilder().startObject().field(ImportIndexType.IMPORT_INDEX_FIELD, importStartIndex).endObject())
      .get();
  }

}
