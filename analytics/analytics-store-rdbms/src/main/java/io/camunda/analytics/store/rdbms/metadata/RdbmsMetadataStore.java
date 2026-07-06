/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import io.camunda.analytics.meter.MeterIdStore;
import io.camunda.analytics.serving.spi.DatasetSpecStore;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.serving.spi.ReportSpecStore;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/**
 * The RDBMS metadata plane: a Liquibase-migrated schema of normalized spec tables plus the meter-id
 * table, read and written through MyBatis mappers, exposed via the neutral {@link MetadataStore}
 * SPI. The {@link DataSource} is an internal detail here — callers depend on the interface, not on
 * JDBC, exactly as they depend on {@link io.camunda.analytics.serving.spi.DatasetStore} for
 * serving.
 */
public final class RdbmsMetadataStore implements MetadataStore {

  private final AnalyticsMetadataSchema schema;
  private final MeterIdStore meterIdStore;
  private final DatasetSpecStore datasetSpecStore;
  private final ReportSpecStore reportSpecStore;

  public RdbmsMetadataStore(final DataSource dataSource) {
    this.schema = new AnalyticsMetadataSchema(dataSource);
    final Environment environment =
        new Environment("analytics-metadata", new JdbcTransactionFactory(), dataSource);
    final Configuration configuration = new Configuration(environment);
    // Column names are UPPER_SNAKE; map them to the row models' camelCase properties, and load the
    // co-located XML mappers (OC's convention) that carry the SQL.
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.addMapper(MeterIdMapper.class);
    configuration.addMapper(DatasetSpecMapper.class);
    configuration.addMapper(ReportSpecMapper.class);
    final SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    this.meterIdStore = new RdbmsMeterIdStore(sessionFactory);
    this.datasetSpecStore = new RdbmsDatasetSpecStore(sessionFactory);
    this.reportSpecStore = new RdbmsReportSpecStore(sessionFactory);
  }

  @Override
  public void migrate() {
    schema.migrate();
  }

  @Override
  public MeterIdStore meterIdStore() {
    return meterIdStore;
  }

  @Override
  public DatasetSpecStore datasetSpecStore() {
    return datasetSpecStore;
  }

  @Override
  public ReportSpecStore reportSpecStore() {
    return reportSpecStore;
  }

  @Override
  public void close() {
    // the DataSource is owned by the caller/wiring layer
  }
}
