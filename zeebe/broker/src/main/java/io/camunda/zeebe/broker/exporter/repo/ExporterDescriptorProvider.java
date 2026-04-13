/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.repo;

import java.util.Collection;

/**
 * A provider that supplies a collection of {@link ExporterDescriptor}s. This is used to allow
 * Spring configurations to register multiple exporters (e.g., for multi-region RDBMS exporting)
 * without requiring individual Spring bean registrations for each descriptor.
 *
 * <p>Implementations of this interface are automatically discovered by Spring's dependency
 * injection (via {@code @Autowired List<ExporterDescriptorProvider>}) and their descriptors are
 * contributed to the {@code ExporterRepository}.
 */
@FunctionalInterface
public interface ExporterDescriptorProvider {
  Collection<ExporterDescriptor> getExporterDescriptors();
}
