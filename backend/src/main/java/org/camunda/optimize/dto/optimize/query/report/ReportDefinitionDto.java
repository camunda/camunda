package org.camunda.optimize.dto.optimize.query.report;

import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;

import java.time.OffsetDateTime;

public class ReportDefinitionDto<RD extends ReportDataDto> implements CollectionEntity {

  protected String id;
  protected String name;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;

  private RD data;

  private final Boolean combined;

  private final ReportType reportType;

  protected ReportDefinitionDto(RD data, Boolean combined, ReportType reportType) {
    this.data = data;
    this.combined = combined;
    this.reportType = reportType;
  }

  public RD getData() {
    return data;
  }

  public void setData(final RD data) {
    this.data = data;
  }

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

  @Override
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

  public Boolean getCombined() {
    return combined;
  }

  public ReportType getReportType() {
    return reportType;
  }

}
