/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.objects;

import java.util.List;

/**
 * One declared object type (e.g. {@code "order"}), compiled and validated by {@link
 * ObjectTypes.Builder#build()} — never constructed directly. Combine with other declared types via
 * {@link CompiledObjectTypes#of} to get the cross-type validation and the fast lookups {@code
 * io.camunda.analytics.lake.translate.LakeTranslator} needs.
 */
public record CompiledObjectType(String name, List<IdentifierSource> identifiers) {

  public CompiledObjectType {
    identifiers = List.copyOf(identifiers);
  }
}
