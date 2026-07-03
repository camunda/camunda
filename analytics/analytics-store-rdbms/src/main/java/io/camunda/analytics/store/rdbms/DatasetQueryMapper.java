/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.SelectProvider;

/**
 * The MyBatis mapper for reading dataset cells. One provider-built dynamic {@code SELECT}; rows
 * come back as column-keyed maps (the column set is per-dataset dynamic), which {@link
 * RdbmsDatasetQueryClient} converts into neutral {@code Cell}s.
 */
public interface DatasetQueryMapper {

  @SelectProvider(type = DatasetQuerySqlProvider.class, method = "fetch")
  List<Map<String, Object>> fetch(Map<String, Object> params);
}
