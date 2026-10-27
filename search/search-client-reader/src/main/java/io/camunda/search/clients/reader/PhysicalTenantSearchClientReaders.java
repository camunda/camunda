/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import java.util.Map;

/**
 * Holds the {@link SearchClientReaders} for every physical tenant backed by a single storage family
 * (ES/OS or RDBMS), keyed by physical tenant id.
 *
 * <p>This wrapper type is not strictly required — a dedicated bean of type {@code Map<String,
 * SearchClientReaders>} would work just as well. It is used to keep the injection point explicit
 * and unambiguous: it avoids any confusion with Spring's bean-collection autowiring, which for a
 * bare {@code Map<String, SearchClientReaders>} injection point keys the map by bean name rather
 * than by physical tenant id.
 *
 * <p>It is a (non-final) class rather than a record so it can be injected as a {@code @Lazy} proxy,
 * deferring construction of the underlying search-client chain until the first read.
 */
public class PhysicalTenantSearchClientReaders {

  private final Map<String, SearchClientReaders> readersByPhysicalTenant;

  public PhysicalTenantSearchClientReaders(
      final Map<String, SearchClientReaders> readersByPhysicalTenant) {
    this.readersByPhysicalTenant = readersByPhysicalTenant;
  }

  public Map<String, SearchClientReaders> readersByPhysicalTenant() {
    return readersByPhysicalTenant;
  }
}
