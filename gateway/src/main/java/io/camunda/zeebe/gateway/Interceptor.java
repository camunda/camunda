/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

public interface Interceptor {

  void intercept(Request request, Control control);

  interface Request {

    String getTarget();

    String getValue(String name);
  }

  interface Control {

    void accept(Request request);

    void reject(Status status, String content);
  }

  enum Status {
    OK,
    NOT_FOUND,
    UNAUTHORIZED,
    BLOCKED
  }
}
