/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import io.camunda.analytics.store.rdbms.metadata.row.MeterIdRow;
import java.util.List;

/**
 * MyBatis mapper for {@code ANALYTICS_METER_ID}. SQL lives in the co-located XML mapper (OC's
 * convention); columns map to {@link MeterIdRow} via {@code mapUnderscoreToCamelCase}.
 */
public interface MeterIdMapper {

  List<MeterIdRow> selectAll();

  void insert(MeterIdRow row);
}
