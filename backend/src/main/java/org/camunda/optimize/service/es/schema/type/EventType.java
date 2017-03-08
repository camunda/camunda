package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EventType extends StrictTypeMappingCreator {

  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String PROCESS_INSTANCE_START_DATE = "processInstanceStartDate";
  public static final String PROCESS_INSTANCE_END_DATE = "processInstanceEndDate";

  @Override
  public String getType() {
    return configurationService.getEventType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    return builder
            .startObject("id")
              .field("type", "keyword")
            .endObject()
            .startObject("activityId")
              .field("type", "keyword")
            .endObject()
            .startObject("state")
              .field("type", "keyword")
            .endObject()
            .startObject("activityInstanceId")
              .field("type", "keyword")
            .endObject()
            .startObject("timestamp")
              .field("type", "date")
            .endObject()
            .startObject("processDefinitionKey")
              .field("type", "keyword")
            .endObject()
            .startObject("processDefinitionId")
              .field("type", "keyword")
            .endObject()
            .startObject("processInstanceId")
              .field("type", "keyword")
            .endObject()
            .startObject(START_DATE)
              .field("type", "date")
              .field("format",configurationService.getDateFormat())
            .endObject()
            .startObject(END_DATE)
              .field("type", "date")
              .field("format",configurationService.getDateFormat())
            .endObject()
            .startObject(PROCESS_INSTANCE_START_DATE)
              .field("type", "date")
              .field("format",configurationService.getDateFormat())
            .endObject()
            .startObject(PROCESS_INSTANCE_END_DATE)
              .field("type", "date")
              .field("format",configurationService.getDateFormat())
            .endObject()
            .startObject("activityType")
              .field("type", "keyword")
            .endObject()
            .startObject("durationInMs")
              .field("type", "long")
            .endObject();
  }
}
