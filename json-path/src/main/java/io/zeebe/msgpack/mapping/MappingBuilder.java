/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import java.util.ArrayList;
import java.util.List;

public class MappingBuilder {

  private final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();

  private List<Mapping> mappings = new ArrayList<>();

  public static Mapping[] createMapping(String source, String target) {
    return createMappings().mapping(source, target).build();
  }

  public static Mapping[] createMapping(String source, String target, Mapping.Type type) {
    return createMappings().mapping(source, target, type).build();
  }

  protected static MappingBuilder createMappings() {
    return new MappingBuilder();
  }

  public MappingBuilder mapping(String source, String target) {
    return mapping(source, target, Mapping.Type.PUT);
  }

  public MappingBuilder mapping(String source, String target, Mapping.Type type) {
    final JsonPathQuery sourceQuery = queryCompiler.compile(source);

    // merging algorithm expect a root object $
    final String targetPath = "$." + target;
    final JsonPathPointer targetPointer = new JsonPathPointer(targetPath.split("\\."));

    mappings.add(new Mapping(sourceQuery, targetPointer, type));
    return this;
  }

  public Mapping[] build() {
    final Mapping[] result = mappings.toArray(new Mapping[mappings.size()]);
    mappings.clear();

    return result;
  }
}
