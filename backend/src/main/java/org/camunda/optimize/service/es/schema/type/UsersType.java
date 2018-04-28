package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class UsersType extends StrictTypeMappingCreator {

  @Override
  public String getType() {
    return configurationService.getElasticSearchUsersType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject("username")
        .field("type", "keyword")
      .endObject()
      .startObject("password")
        .field("type", "keyword")
      .endObject();
  }

}
