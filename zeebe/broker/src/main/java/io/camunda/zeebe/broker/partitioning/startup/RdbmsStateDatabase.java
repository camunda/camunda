/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import java.util.Objects;
import javax.sql.DataSource;
import org.jspecify.annotations.NullMarked;

/**
 * Where a physical tenant keeps its partitions' state when {@code
 * camunda.data.primary-storage.type} is {@code rdbms}: the tenant's history database, reached
 * through a pool of its own.
 *
 * @param dataSource the state's pool; it is owned by whoever created it and never closed by the
 *     broker
 * @param prefix the tenant's table prefix, the same as its history tables'
 */
@NullMarked
public record RdbmsStateDatabase(DataSource dataSource, String prefix) {

  public RdbmsStateDatabase {
    Objects.requireNonNull(dataSource, "must specify a data source");
    Objects.requireNonNull(prefix, "must specify a prefix");
  }
}
