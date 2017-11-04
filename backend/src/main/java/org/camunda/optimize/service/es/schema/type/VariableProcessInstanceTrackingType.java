package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class VariableProcessInstanceTrackingType extends StrictTypeMappingCreator {

  public static final String VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE = "variable-process-instance-tracking";

  public static final String PROCESS_INSTANCE_IDS = "processInstanceIds";

  @Override
  public String getType() {
    return VARIABLE_PROCESS_INSTANCE_TRACKING_TYPE;
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    return builder
      .startObject(PROCESS_INSTANCE_IDS)
        .field("type", "keyword")
      .endObject();
  }
}
