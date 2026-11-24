/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.state;

import io.camunda.zeebe.metadata.store.api.KeyEvent;
import io.camunda.zeebe.metadata.store.api.NamespaceChange;

/**
 * One change that applying an entry made, as the watch registry consumes it. A deleted namespace
 * gives a {@link KeyChanged} removal for each of its keys first, then its {@link NamespaceChanged}.
 */
public sealed interface StoreChange {

  /** A key was added, replaced or removed. */
  record KeyChanged(KeyEvent event) implements StoreChange {}

  /** A namespace was created or deleted. */
  record NamespaceChanged(NamespaceChange change) implements StoreChange {}
}
