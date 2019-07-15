/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;

/**
 * Represents a mapping to map from one message pack document to another. The mapping has a json
 * path query for the source and a json path string which points to the target.
 *
 * <p>This makes it possible to map a part of a message pack document into a new/existing document.
 * With the mapping it is possible to replace/rename objects.
 */
public class Mapping {
  public static final String JSON_ROOT_PATH = "$";
  public static final String MAPPING_STRING = "%s -> %s";

  private final JsonPathQuery source;
  private final JsonPathPointer targetPointer;
  private final Type type;

  public Mapping(JsonPathQuery source, JsonPathPointer targetPointer, Mapping.Type type) {
    this.source = source;
    this.targetPointer = targetPointer;
    this.type = type;
  }

  public JsonPathQuery getSource() {
    return this.source;
  }

  public JsonPathPointer getTargetPointer() {
    return targetPointer;
  }

  public Type getType() {
    return type;
  }

  public boolean mapsToRootPath() {
    return targetPointer.getPathElements().length == 1;
  }

  @Override
  public String toString() {
    return String.format(
        MAPPING_STRING, new String(source.getExpression().byteArray()), targetPointer);
  }

  public enum Type {
    PUT,
    COLLECT
  }
}
