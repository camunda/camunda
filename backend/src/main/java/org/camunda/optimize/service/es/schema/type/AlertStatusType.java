package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Askar Akhmerov
 */
@Component
public class AlertStatusType extends StrictTypeMappingCreator {
  public static final String ID = "id";
  public static final String TRIGGERED = "triggered";

  @Override
  public String getType() {
    return configurationService.getAlertStatusType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    XContentBuilder newBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(TRIGGERED)
        .field("type", "boolean")
      .endObject();

    return newBuilder;
  }

}
