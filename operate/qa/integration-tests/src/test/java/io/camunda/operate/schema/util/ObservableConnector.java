/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util;

import java.util.function.Consumer;

public interface ObservableConnector {

  void addRequestListener(Consumer<OperateTestHttpRequest> listener);

  void clearRequestListeners();

  /** Adapter for HttpClient 4 and 5 */
  public interface OperateTestHttpRequest {

    String getUri();

    String getMethod();
  }
}
