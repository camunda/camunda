/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.Objects;

/**
 * One namespace created or deleted, as {@link NamespaceApi#watchNamespace} delivers it.
 *
 * @param namespace the namespace that changed
 * @param type what happened to it
 */
public record NamespaceChange(Namespace namespace, ChangeType type) {

  public NamespaceChange {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(type, "type");
  }

  /**
   * What happened to a namespace. New kinds may be added; a consumer that switches over them keeps
   * a default branch.
   */
  public enum ChangeType {
    CREATED,
    DELETED
  }
}
