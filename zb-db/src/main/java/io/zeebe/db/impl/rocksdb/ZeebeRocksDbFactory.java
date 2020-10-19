/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl.rocksdb;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public final class ZeebeRocksDbFactory<ColumnFamilyType extends Enum<ColumnFamilyType>>
    implements ZeebeDbFactory<ColumnFamilyType> {

  static {
    RocksDB.loadLibrary();
  }

  private final Class<ColumnFamilyType> columnFamilyTypeClass;
  private final Properties userProvidedColumnFamilyOptions;

  private ZeebeRocksDbFactory(
      final Class<ColumnFamilyType> columnFamilyTypeClass,
      final Properties userProvidedColumnFamilyOptions) {
    this.columnFamilyTypeClass = columnFamilyTypeClass;
    this.userProvidedColumnFamilyOptions = Objects.requireNonNull(userProvidedColumnFamilyOptions);
  }

  public static <ColumnFamilyType extends Enum<ColumnFamilyType>>
      ZeebeDbFactory<ColumnFamilyType> newFactory(
          final Class<ColumnFamilyType> columnFamilyTypeClass) {
    final var columnFamilyOptions = new Properties();
    return new ZeebeRocksDbFactory<>(columnFamilyTypeClass, columnFamilyOptions);
  }

  public static <ColumnFamilyType extends Enum<ColumnFamilyType>>
      ZeebeDbFactory<ColumnFamilyType> newFactory(
          final Class<ColumnFamilyType> columnFamilyTypeClass,
          final Properties userProvidedColumnFamilyOptions) {
    return new ZeebeRocksDbFactory<>(columnFamilyTypeClass, userProvidedColumnFamilyOptions);
  }

  @Override
  public ZeebeTransactionDb<ColumnFamilyType> createDb(final File pathName) {
    return open(
        pathName,
        Arrays.stream(columnFamilyTypeClass.getEnumConstants())
            .map(c -> c.name().toLowerCase().getBytes())
            .collect(Collectors.toList()));
  }

  private ZeebeTransactionDb<ColumnFamilyType> open(
      final File dbDirectory, final List<byte[]> columnFamilyNames) {

    final ZeebeTransactionDb<ColumnFamilyType> db;
    try {
      final List<AutoCloseable> closeables = new ArrayList<>();

      // column family options have to be closed as last
      final ColumnFamilyOptions columnFamilyOptions = createColumnFamilyOptions();
      closeables.add(columnFamilyOptions);

      final List<ColumnFamilyDescriptor> columnFamilyDescriptors =
          createFamilyDescriptors(columnFamilyNames, columnFamilyOptions);
      final DBOptions dbOptions =
          new DBOptions()
              .setCreateMissingColumnFamilies(true)
              .setErrorIfExists(false)
              .setCreateIfMissing(true)
              .setParanoidChecks(true);
      closeables.add(dbOptions);

      db =
          ZeebeTransactionDb.openTransactionalDb(
              dbOptions,
              dbDirectory.getAbsolutePath(),
              columnFamilyDescriptors,
              closeables,
              columnFamilyTypeClass);

    } catch (final RocksDBException e) {
      throw new RuntimeException("Unexpected error occurred trying to open the database", e);
    }
    return db;
  }

  private List<ColumnFamilyDescriptor> createFamilyDescriptors(
      final List<byte[]> columnFamilyNames, final ColumnFamilyOptions columnFamilyOptions) {
    final List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();

    if (columnFamilyNames != null && !columnFamilyNames.isEmpty()) {
      for (final byte[] name : columnFamilyNames) {
        final ColumnFamilyDescriptor columnFamilyDescriptor =
            new ColumnFamilyDescriptor(name, columnFamilyOptions);
        columnFamilyDescriptors.add(columnFamilyDescriptor);
      }
    }
    return columnFamilyDescriptors;
  }

  /** @return Options which are used on all column families */
  public ColumnFamilyOptions createColumnFamilyOptions() {
    // start with some defaults
    final var columnFamilyOptionProps = new Properties();
    // look for cf_options.h to find available keys
    // look for options_helper.cc to find available values
    columnFamilyOptionProps.put("compaction_pri", "kOldestSmallestSeqFirst");

    // apply custom options
    columnFamilyOptionProps.putAll(userProvidedColumnFamilyOptions);

    final var columnFamilyOptions =
        ColumnFamilyOptions.getColumnFamilyOptionsFromProps(columnFamilyOptionProps);
    if (columnFamilyOptions == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to create column family options for RocksDB, "
                  + "but one or many values are undefined in the context of RocksDB "
                  + "[Compiled ColumnFamilyOptions: %s; User-provided ColumnFamilyOptions: %s]. "
                  + "See RocksDB's cf_options.h and options_helper.cc for available keys and values.",
              columnFamilyOptionProps, userProvidedColumnFamilyOptions));
    }
    return columnFamilyOptions;
  }
}
