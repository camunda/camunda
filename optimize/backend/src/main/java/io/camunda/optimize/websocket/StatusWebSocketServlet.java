/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.websocket;

import java.io.Serial;
import lombok.RequiredArgsConstructor;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketServletFactory;

@RequiredArgsConstructor
public class StatusWebSocketServlet extends JettyWebSocketServlet {

  @Serial private static final long serialVersionUID = 4602610830296058744L;

  @Override
  protected void configure(final JettyWebSocketServletFactory factory) {
    factory.register(StatusWebSocket.class);
  }
}
