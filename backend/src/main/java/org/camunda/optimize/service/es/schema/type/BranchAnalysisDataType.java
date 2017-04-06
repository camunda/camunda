package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class BranchAnalysisDataType extends StrictTypeMappingCreator {

  public final static String ACTIVITY_LIST = "activityList";
  public static final String PROCESS_INSTANCE_START_DATE = "processInstanceStartDate";
  public static final String PROCESS_INSTANCE_END_DATE = "processInstanceEndDate";

  @Override
  public String getType() {
    return configurationService.getBranchAnalysisDataType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(ACTIVITY_LIST)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_INSTANCE_START_DATE)
        .field("type", "date")
        .field("format",configurationService.getDateFormat())
      .endObject()
      .startObject(PROCESS_INSTANCE_END_DATE)
        .field("type", "date")
        .field("format",configurationService.getDateFormat())
      .endObject()
      .startObject("processDefinitionId")
        .field("type", "keyword")
      .endObject();
  }

}
