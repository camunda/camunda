package org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

import java.time.OffsetDateTime;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.FIXED_DATE_FILTER;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.RELATIVE_DATE_FILTER;

/**
 * Abstract class that contains a hidden "type" field to distinguish, which
 * filter type the jackson object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RelativeStartDateFilterDataDto.class, name = RELATIVE_DATE_FILTER),
    @JsonSubTypes.Type(value = FixedStartDateFilterDataDto.class, name = FIXED_DATE_FILTER),
})

public abstract class StartDateFilterDataDto<START> implements FilterDataDto {

  @JsonProperty
  protected String type;

  protected START start;
  protected OffsetDateTime end;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public OffsetDateTime getEnd() {
    return end;
  }

  public void setEnd(OffsetDateTime end) {
    this.end = end;
  }

  public START getStart() {
    return start;
  }

  public void setStart(START start) {
    this.start = start;
  }
}
