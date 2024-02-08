/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;

/**
 * RocksDB has separate options for the database and the column families. Zeebe configuration can
 * change these depending on its own configuration. As each of the parts must be individually
 * closed, this record allows easily passing both configurations around within Zeebe.
 *
 * <p>While each column family in RocksDB can be configured differently, Zeebe only uses a single
 * RocksDB column family. We therefore don't have to differentiate further than a single database
 * options and a single column family options.
 *
 * @param dbOptions The database options used to open the RocksDB database
 * @param cfOptions The column family options used to open the RocksDB database
 */
public record RocksDbOptions(DBOptions dbOptions, ColumnFamilyOptions cfOptions) {}
