/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class Mappings {

  private final Mapping[] mappings;

  private final Set<DirectBuffer> sourceVariableNames;
  private final Set<DirectBuffer> targetVariableNames;

  public Mappings() {
    this(new Mapping[0]);
  }

  public Mappings(final Mapping[] mappings) {
    this.mappings = mappings;

    sourceVariableNames =
        Arrays.stream(mappings)
            .map(Mapping::getSource)
            .map(JsonPathQuery::getVariableName)
            .collect(Collectors.toSet());

    targetVariableNames =
        Arrays.stream(mappings)
            .map(Mapping::getTargetPointer)
            .map(JsonPathPointer::getVariableName)
            .collect(Collectors.toSet());
  }

  public boolean isEmpty() {
    return mappings.length == 0;
  }

  public Mapping[] get() {
    return mappings;
  }

  public Set<DirectBuffer> getSourceVariableNames() {
    return sourceVariableNames;
  }

  public Set<DirectBuffer> getTargetVariableNames() {
    return targetVariableNames;
  }

  @Override
  public String toString() {
    return "Mappings{" + Arrays.toString(mappings) + '}';
  }
}
