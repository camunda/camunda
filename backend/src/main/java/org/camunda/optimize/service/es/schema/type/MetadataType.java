package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_TYPE_SCHEMA_VERSION;


@Component
public class MetadataType extends StrictTypeMappingCreator {

  public static final String SCHEMA_VERSION = METADATA_TYPE_SCHEMA_VERSION;

  @Override
  public String getType() {
    return configurationService.getMetaDataType();
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(SCHEMA_VERSION)
        .field("type", "keyword")
      .endObject();
  }
}
