/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import io.camunda.analytics.report.ReportDefinition;
import io.camunda.analytics.serving.spi.ReportSpecStore;
import io.camunda.analytics.store.rdbms.metadata.row.ReportSpecRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * The RDBMS {@link ReportSpecStore}: one {@code ANALYTICS_REPORT_SPEC} row per report — the
 * searchable scalar columns ({@code report_id}/{@code name}) plus the full {@link ReportDefinition}
 * as a JSON {@code SPEC} column. The report is small, config-like and read wholesale, so it is
 * stored as a document rather than normalized: {@code create} allocates the next id (highest stored
 * + 1, mirroring how datasets allocate {@code cubeId}) and inserts, {@code read} looks up by id,
 * {@code search} returns all rows, {@code delete} removes one; the JSON is deserialized back into
 * the domain model.
 */
final class RdbmsReportSpecStore implements ReportSpecStore {

  private final SqlSessionFactory sessionFactory;
  private final ReportSpecJson reportSpecJson = new ReportSpecJson();

  RdbmsReportSpecStore(final SqlSessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public void create(final ReportDefinition report) {
    try (SqlSession session = sessionFactory.openSession()) {
      final ReportSpecMapper mapper = session.getMapper(ReportSpecMapper.class);
      final long reportId = mapper.maxReportId() + 1;
      final ReportDefinition stored =
          new ReportDefinition(
              reportId,
              report.name(),
              report.sources(),
              report.groupBy(),
              report.granularityMs(),
              report.combination(),
              report.viz());
      mapper.insertReport(
          new ReportSpecRow(reportId, stored.name(), reportSpecJson.toJson(stored)));
      session.commit();
    }
  }

  @Override
  public Optional<ReportDefinition> read(final long reportId) {
    try (SqlSession session = sessionFactory.openSession()) {
      final ReportSpecRow row =
          session.getMapper(ReportSpecMapper.class).selectReportById(reportId);
      return row == null ? Optional.empty() : Optional.of(reportSpecJson.fromJson(row.getSpec()));
    }
  }

  @Override
  public List<ReportDefinition> search() {
    try (SqlSession session = sessionFactory.openSession()) {
      final List<ReportSpecRow> rows = session.getMapper(ReportSpecMapper.class).selectAll();
      final List<ReportDefinition> reports = new ArrayList<>(rows.size());
      for (final ReportSpecRow row : rows) {
        reports.add(reportSpecJson.fromJson(row.getSpec()));
      }
      return reports;
    }
  }

  @Override
  public void delete(final long reportId) {
    try (SqlSession session = sessionFactory.openSession()) {
      session.getMapper(ReportSpecMapper.class).deleteReport(reportId);
      session.commit();
    }
  }
}
