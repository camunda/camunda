/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.serde;

import java.io.IOException;

public interface OutputFormatter {
  <T> void write(final T value, final Class<T> type) throws IOException;

  <T> String serialize(final T value, final Class<T> type) throws IOException;
}
