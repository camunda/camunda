/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

import static io.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import io.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import org.agrona.DirectBuffer;

public final class JsonPathPointer {

  private final DirectBuffer variableName;

  private final String[] pathElements;

  public JsonPathPointer(final String path) {
    ensureNotNullOrEmpty("path", path);

    // merging algorithm expect a root object $
    pathElements = ("$." + path).split("\\.");

    final String variableElement = pathElements[1];
    variableName = BufferUtil.wrapString(variableElement);
  }

  public String[] getPathElements() {
    return pathElements;
  }

  public DirectBuffer getVariableName() {
    return variableName;
  }

  @Override
  public String toString() {
    return Arrays.toString(pathElements);
  }
}
