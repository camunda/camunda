/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.storage.atomix;

import io.atomix.raft.zeebe.ZeebeLogAppender;
import java.util.Optional;

@FunctionalInterface
public interface AtomixAppenderSupplier {
  Optional<ZeebeLogAppender> getAppender();
}
