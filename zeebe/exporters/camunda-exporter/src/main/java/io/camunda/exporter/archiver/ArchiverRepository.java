/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

/** Placeholder interface for future abstracted access to the underlying storage (e.g. ES/OS). */
public interface ArchiverRepository extends AutoCloseable {
  static final class NoopArchiverRepository implements ArchiverRepository {

    @Override
    public void close() throws Exception {}
  }
}
