package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LicenseType extends StrictTypeMappingCreator {
  public static final int VERSION = 1;


  public static final String LICENSE = "license";

  @Override
  public String getType() {
    return ElasticsearchConstants.LICENSE_TYPE;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(LICENSE)
        .field("type", "text")
        .field("index", false)
      .endObject();
  }

}
