package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ImportIndexType extends StrictTypeMappingCreator {

  public static final String IMPORT_INDEX_FIELD = "importIndex";

  @Override
  public String getType() {
    return configurationService.getImportIndexType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(IMPORT_INDEX_FIELD)
        .field("type", "integer")
      .endObject();
  }
}
