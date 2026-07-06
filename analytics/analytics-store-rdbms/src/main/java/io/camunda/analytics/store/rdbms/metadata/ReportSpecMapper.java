/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import io.camunda.analytics.store.rdbms.metadata.row.ReportSpecRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for {@code ANALYTICS_REPORT_SPEC}. SQL lives in the co-located XML mapper (OC's
 * convention); columns map to {@link ReportSpecRow} via {@code mapUnderscoreToCamelCase}. The
 * report is a JSON document column, so there are no child tables — just the searchable scalar
 * columns and the blob.
 */
public interface ReportSpecMapper {

  /**
   * The highest stored {@code reportId}, or {@code 0} if the table is empty (drives allocation).
   */
  long maxReportId();

  List<ReportSpecRow> selectAll();

  ReportSpecRow selectReportById(@Param("reportId") long reportId);

  void insertReport(ReportSpecRow row);

  void deleteReport(@Param("reportId") long reportId);
}
