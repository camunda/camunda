package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Askar Akhmerov
 */
@Component
public class ShareType extends StrictTypeMappingCreator {

  public static final String ID = "id";
  public static final String TYPE = "type";
  public static final String RESOURCE_ID = "resourceId";

  @Override
  public String getType() {
    return configurationService.getShareType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    XContentBuilder newBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(RESOURCE_ID)
        .field("type", "keyword")
      .endObject();

    return newBuilder;
  }
}
