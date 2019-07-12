/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport;

public class Transports {
  public static ServerTransportBuilder newServerTransport() {
    return new ServerTransportBuilder();
  }

  public static ClientTransportBuilder newClientTransport(final String name) {
    return new ClientTransportBuilder(name);
  }
}
