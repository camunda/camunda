package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DurationHeatmapTargetValueType extends StrictTypeMappingCreator {

  public final static String ACTIVITY_ID = "activityId";
  public final static String TARGET_VALUE_LIST = "activityTargetValueList";
  public static final String TARGET_VALUE = "targetValue";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";

  @Override
  public String getType() {
    return configurationService.getDurationHeatmapTargetValueType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(TARGET_VALUE_LIST)
        .field("type", "nested")
        .startObject("properties")
          .startObject(ACTIVITY_ID)
            .field("type", "keyword")
          .endObject()
          .startObject(TARGET_VALUE)
            .field("type", "keyword")
          .endObject()
        .endObject()
      .endObject()
      .startObject(PROCESS_DEFINITION_ID)
        .field("type", "keyword")
      .endObject();
  }
}
