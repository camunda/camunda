package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ReportType extends StrictTypeMappingCreator {

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String LAST_MODIFIED = "lastModified";
  public static final String CREATED = "created";
  public static final String OWNER = "owner";
  public static final String LAST_MODIFIER = "lastModifier";

  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";

  public static final String DATA = "data";

  @Override
  public String getType() {
    return configurationService.getReportType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
     XContentBuilder newBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIED)
        .field("type", "date")
              .field("format",configurationService.getDateFormat())
      .endObject()
      .startObject(CREATED)
        .field("type", "date")
              .field("format",configurationService.getDateFormat())
      .endObject()
      .startObject(OWNER)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIER)
        .field("type", "keyword")
      .endObject()
      .startObject(DATA)
        .field("type", "text")
        .field("index", false)
      .endObject();
     return newBuilder;
  }

}
