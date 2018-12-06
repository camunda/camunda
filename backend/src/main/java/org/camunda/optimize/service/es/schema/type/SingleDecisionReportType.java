package org.camunda.optimize.service.es.schema.type;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;

@Component
public class SingleDecisionReportType extends AbstractReportType {

  @Override
  public String getType() {
    return SINGLE_DECISION_REPORT_TYPE;
  }

  @Override
  protected XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder.
      startObject(DATA)
        .field("enabled", false)
      .endObject();
  }
}
