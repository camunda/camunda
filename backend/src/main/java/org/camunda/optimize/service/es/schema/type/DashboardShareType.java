package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Askar Akhmerov
 */
@Component
public class DashboardShareType extends StrictTypeMappingCreator {

  public static final String ID = "id";
  public static final String TYPE = "type";
  public static final String DASHBOARD_ID = "dashboardId";
  public static final String REPORT_SHARES = "reportShares";

  @Override
  public String getType() {
    return configurationService.getDashboardShareType();
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
      .startObject(REPORT_SHARES)
        .field("type", "keyword")
      .endObject()
      .startObject(DASHBOARD_ID)
        .field("type", "keyword")
      .endObject();

    return newBuilder;
  }
}
