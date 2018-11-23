package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;

import java.time.OffsetDateTime;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_REPORT_TYPE;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "reportType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SingleReportDefinitionDto.class, name = SINGLE_REPORT_TYPE),
    @JsonSubTypes.Type(value = CombinedReportDefinitionDto.class, name = COMBINED_REPORT_TYPE),
})
public abstract class ReportDefinitionDto<DATA extends ReportDataDto> {

  protected String id;
  protected String name;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;

  @JsonProperty
  protected String reportType;
  protected DATA data;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public OffsetDateTime getLastModified() {
    return lastModified;
  }

  public OffsetDateTime getCreated() {
    return created;
  }

  public void setCreated(OffsetDateTime created) {
    this.created = created;
  }

  public void setLastModified(OffsetDateTime lastModified) {
    this.lastModified = lastModified;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getLastModifier() {
    return lastModifier;
  }

  public void setLastModifier(String lastModifier) {
    this.lastModifier = lastModifier;
  }

  public DATA getData() {
    return data;
  }

  public void setData(DATA data) {
    this.data = data;
  }

  public String getReportType() {
    return reportType;
  }

  public void setReportType(String reportType) {
    this.reportType = reportType;
  }
}
