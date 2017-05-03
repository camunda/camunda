package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class VariableType extends StrictTypeMappingCreator {

  @Override
  public String getType() {
    return configurationService.getVariableType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    return builder;
  }
}

