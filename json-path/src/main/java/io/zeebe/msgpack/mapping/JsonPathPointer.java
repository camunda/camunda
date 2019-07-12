/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

import java.util.Arrays;

public class JsonPathPointer {

  private final String[] pathElements;

  public JsonPathPointer(String[] pathElements) {
    this.pathElements = pathElements;
  }

  public String[] getPathElements() {
    return pathElements;
  }

  @Override
  public String toString() {
    return Arrays.toString(pathElements);
  }
}
