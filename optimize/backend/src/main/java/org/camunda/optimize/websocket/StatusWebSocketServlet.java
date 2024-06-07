/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.websocket;

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
