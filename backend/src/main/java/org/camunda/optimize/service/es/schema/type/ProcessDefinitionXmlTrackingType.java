package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProcessDefinitionXmlTrackingType extends StrictTypeMappingCreator {

  public static final String PROCESS_DEFINITION_XML_TRACKING_TYPE = "process-definition-xml-tracking";

  public static final String PROCESS_DEFINITION_IDS = "ids";

  @Override
  public String getType() {
    return PROCESS_DEFINITION_XML_TRACKING_TYPE;
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    return builder
      .startObject(PROCESS_DEFINITION_IDS)
        .field("type", "keyword")
      .endObject();
  }
}
