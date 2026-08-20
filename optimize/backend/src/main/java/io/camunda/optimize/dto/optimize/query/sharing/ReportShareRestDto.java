/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sharing;

import java.io.Serializable;
import java.util.Objects;

public class ReportShareRestDto implements Serializable {

  private String id;
  private String reportId;

  public ReportShareRestDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getReportId() {
    return reportId;
  }

  public void setReportId(final String reportId) {
    this.reportId = reportId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportShareRestDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ReportShareRestDto that = (ReportShareRestDto) o;
    return Objects.equals(id, that.id) && Objects.equals(reportId, that.reportId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, reportId);
  }

  @Override
  public String toString() {
    return "ReportShareRestDto(id=" + getId() + ", reportId=" + getReportId() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String id = "id";
    public static final String reportId = "reportId";
  }
}
