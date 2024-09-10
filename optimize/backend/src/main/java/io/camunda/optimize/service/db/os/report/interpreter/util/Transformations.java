/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.util;

import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public class Transformations {
  public static <A, B> Map<A, B> pairToMap(Pair<A, B> pair) {
    return Map.of(pair.getKey(), pair.getValue());
  }
}
