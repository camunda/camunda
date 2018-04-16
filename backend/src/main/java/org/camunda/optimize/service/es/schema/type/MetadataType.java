package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Askar Akhmerov
 */
@Component
public class MetadataType extends StrictTypeMappingCreator {

  private static final String SCHEMA_VERSION = "schemaVersion";

  @Override
  public String getType() {
    return configurationService.getMeataDataType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(SCHEMA_VERSION)
        .field("type", "keyword")
      .endObject();
  }
}
