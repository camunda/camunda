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
import org.rocksdb.CompactionPriority;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public final class ZeebeRocksDbFactory<ColumnFamilyType extends Enum<ColumnFamilyType>>
    implements ZeebeDbFactory<ColumnFamilyType> {

  static {
    RocksDB.loadLibrary();
  }

  private final Class<ColumnFamilyType> columnFamilyTypeClass;
  private final RocksDbConfiguration rocksDbConfiguration;

  private ZeebeRocksDbFactory(
      final Class<ColumnFamilyType> columnFamilyTypeClass,
      final RocksDbConfiguration rocksDbConfiguration) {
    this.columnFamilyTypeClass = columnFamilyTypeClass;
    this.rocksDbConfiguration = Objects.requireNonNull(rocksDbConfiguration);
  }

  public static <ColumnFamilyType extends Enum<ColumnFamilyType>>
      ZeebeDbFactory<ColumnFamilyType> newFactory(
          final Class<ColumnFamilyType> columnFamilyTypeClass) {
    return new ZeebeRocksDbFactory<>(columnFamilyTypeClass, new RocksDbConfiguration());
  }

  public static <ColumnFamilyType extends Enum<ColumnFamilyType>>
      ZeebeDbFactory<ColumnFamilyType> newFactory(
          final Class<ColumnFamilyType> columnFamilyTypeClass,
          final RocksDbConfiguration rocksDbConfiguration) {
    return new ZeebeRocksDbFactory<>(columnFamilyTypeClass, rocksDbConfiguration);
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
      final DBOptions dbOptions = createDefaultDbOptions();
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
    final var userProvidedColumnFamilyOptions = rocksDbConfiguration.getColumnFamilyOptions();
    final var hasUserOptions = !userProvidedColumnFamilyOptions.isEmpty();

    if (hasUserOptions) {
      return createFromUserOptions(userProvidedColumnFamilyOptions);
    }

    return createDefaultColumnFamilyOptions();
  }

  private ColumnFamilyOptions createDefaultColumnFamilyOptions() {
    final var columnFamilyOptions = new ColumnFamilyOptions();
    return columnFamilyOptions.setCompactionPriority(CompactionPriority.OldestSmallestSeqFirst);
  }

  private ColumnFamilyOptions createFromUserOptions(
      final Properties userProvidedColumnFamilyOptions) {
    final var columnFamilyOptions =
        ColumnFamilyOptions.getColumnFamilyOptionsFromProps(userProvidedColumnFamilyOptions);
    if (columnFamilyOptions == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to create column family options for RocksDB, "
                  + "but one or many values are undefined in the context of RocksDB "
                  + "[User-provided ColumnFamilyOptions: %s]. "
                  + "See RocksDB's cf_options.h and options_helper.cc for available keys and values.",
              userProvidedColumnFamilyOptions));
    }
    return columnFamilyOptions;
  }

  private DBOptions createDefaultDbOptions() {
    return new DBOptions()
        .setCreateMissingColumnFamilies(true)
        .setErrorIfExists(false)
        .setCreateIfMissing(true)
        .setParanoidChecks(true);
  }
}
