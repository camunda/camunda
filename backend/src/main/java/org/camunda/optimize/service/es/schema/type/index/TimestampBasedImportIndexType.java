package org.camunda.optimize.service.es.schema.type.index;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TimestampBasedImportIndexType extends StrictTypeMappingCreator {

  public static final String TIMESTAMP_OF_LAST_ENTITY = "timestampOfLastEntity";
  public static final String ES_TYPE_INDEX_REFERS_TO = "esTypeIndexRefersTo";
  private static final String ENGINE = "engine";

  public static final String TIMESTAMP_BASED_IMPORT_INDEX_TYPE = "timestamp-based-import-index";

  @Override
  public String getType() {
    return TIMESTAMP_BASED_IMPORT_INDEX_TYPE;
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(ES_TYPE_INDEX_REFERS_TO)
        .field("type", "keyword")
      .endObject()
      .startObject(TIMESTAMP_OF_LAST_ENTITY)
        .field("type", "date")
        .field("format",configurationService.getOptimizeDateFormat())
      .endObject();
  }
}
