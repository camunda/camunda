/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Where {@link ExportersState} keeps the position and metadata of each exporter of a partition:
 * RocksDB by default, or the relational database of the primary storage, so that a broker that
 * takes the partition over continues exporting where its predecessor stopped.
 */
public interface ExporterStateStore {

  Optional<ExporterStateEntry> find(String exporterId);

  void upsert(String exporterId, ExporterStateEntry entry);

  void delete(String exporterId);

  void forEach(BiConsumer<String, ExporterStateEntry> consumer);

  boolean isEmpty();
}
