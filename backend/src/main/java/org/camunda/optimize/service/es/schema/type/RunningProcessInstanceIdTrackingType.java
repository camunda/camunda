package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RunningProcessInstanceIdTrackingType extends StrictTypeMappingCreator {

  @Override
  public String getType() {
    return configurationService.getUnfinishedProcessInstanceIdTrackingType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    return builder; // no content expected here
  }
}
