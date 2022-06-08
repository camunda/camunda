/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.websocket;

import lombok.RequiredArgsConstructor;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

@RequiredArgsConstructor
public class StatusWebSocketServlet extends WebSocketServlet {
  @Override
  public void configure(WebSocketServletFactory factory) {
    factory.register(StatusWebSocket.class);
  }
}
