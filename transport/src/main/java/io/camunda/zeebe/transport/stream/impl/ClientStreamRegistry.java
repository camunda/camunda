/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import java.util.Collection;
import java.util.UUID;

public class ClientStreamRegistry {
  public void add(final ClientStream clientStream) {}

  public ClientStream remove(final UUID streamId) {}

  public Collection<ClientStream> list() {}

  public void clear() {}
}
