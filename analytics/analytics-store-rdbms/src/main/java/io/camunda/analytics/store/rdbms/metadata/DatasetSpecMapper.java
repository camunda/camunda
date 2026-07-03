/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import io.camunda.analytics.store.rdbms.metadata.row.ActivationRow;
import io.camunda.analytics.store.rdbms.metadata.row.DatasetRow;
import io.camunda.analytics.store.rdbms.metadata.row.DimensionRow;
import io.camunda.analytics.store.rdbms.metadata.row.FilterRow;
import io.camunda.analytics.store.rdbms.metadata.row.MeterParamRow;
import io.camunda.analytics.store.rdbms.metadata.row.MeterRow;
import io.camunda.analytics.store.rdbms.metadata.row.WindowRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for the normalized dataset-spec tables. SQL lives in the co-located XML mapper
 * (OC's convention); columns map to the row models via {@code mapUnderscoreToCamelCase}. {@link
 * RdbmsDatasetSpecStore} assembles the rows back into {@link
 * io.camunda.analytics.dataset.RegisteredDataset}s.
 */
public interface DatasetSpecMapper {

  long countDatasets();

  /** Dynamic search: each non-null argument is an equality filter (see the XML {@code <where>}). */
  List<DatasetRow> searchDatasets(
      @Param("name") String name,
      @Param("sourceFact") String sourceFact,
      @Param("kind") String kind);

  DatasetRow selectDatasetById(@Param("cubeId") long cubeId);

  void insertDataset(DatasetRow row);

  List<FilterRow> selectFilters();

  void insertFilter(FilterRow row);

  List<DimensionRow> selectDimensions();

  void insertDimension(DimensionRow row);

  List<MeterRow> selectMeters();

  void insertMeter(MeterRow row);

  List<MeterParamRow> selectMeterParams();

  void insertMeterParam(MeterParamRow row);

  List<WindowRow> selectWindows();

  void insertWindow(WindowRow row);

  List<ActivationRow> selectActivation();

  void insertActivation(ActivationRow row);
}
