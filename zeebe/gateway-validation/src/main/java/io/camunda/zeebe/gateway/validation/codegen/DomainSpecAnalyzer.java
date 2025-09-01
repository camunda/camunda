/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation.codegen;

import io.camunda.zeebe.gateway.validation.model.GroupDescriptor;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Placeholder for build-time domain spec analysis. This will parse rest-api.domain.yaml and produce
 * GroupDescriptor instances which will then be emitted as generated source. Currently a stub
 * returning an empty list.
 */
public final class DomainSpecAnalyzer {

  public List<GroupDescriptor> analyze(final Path specPath) {
    // TODO implement real parsing & extraction
    return Collections.emptyList();
  }
}
