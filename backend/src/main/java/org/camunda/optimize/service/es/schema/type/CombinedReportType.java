package org.camunda.optimize.service.es.schema.type;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CombinedReportType extends AbstractReportType {

  public static final String REPORT_IDS = "reportIds";
  public static final String CONFIGURATION = "configuration";

  public static final String COMBINED_REPORT_TYPE = "combined-report";

  @Override
  public String getType() {
    return COMBINED_REPORT_TYPE;
  }

  @Override
  protected XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder.
      startObject(DATA)
        .field("type", "nested")
        .startObject("properties")
          .startObject(CONFIGURATION)
            .field("enabled", false)
          .endObject()
          .startObject(REPORT_IDS)
            .field("type", "keyword")
          .endObject()
        .endObject()
      .endObject();
  }
}
