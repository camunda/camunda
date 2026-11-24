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
 * One change of one namespace, as {@link NamespaceApi#watchNamespace} delivers it.
 *
 * @param type what the change did to the namespace
 * @param namespace the namespace that changed
 */
public record NamespaceEvent(ChangeType type, Namespace namespace) {

  public NamespaceEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(namespace, "namespace");
  }

  /**
   * What a change did to a namespace. New kinds may be added; a consumer that switches over them
   * keeps a default branch.
   */
  public enum ChangeType {
    CREATED,
    DELETED
  }
}
