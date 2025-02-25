/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import java.io.IOException;
import java.io.InputStream;

public class TestStreamHelper {

  public static void fullyConsumeStream(final InputStream stream) throws IOException {
    while (stream.read() != -1) {}
  }
}
