/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

/** Whose data a {@link Namespace} holds. */
public enum NamespaceScope {
  /** The whole cluster; the namespace name is a fixed name chosen by its consumer. */
  CLUSTER,
  /** One physical tenant; the namespace name is the physical tenant id. */
  PHYSICAL_TENANT
}
