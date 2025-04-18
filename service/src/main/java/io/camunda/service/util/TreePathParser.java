/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TreePathParser {

  private TreePathParser() {}

  public static List<String> extractProcessInstanceKeys(final String treePath) {
    final List<String> processInstanceIds = new ArrayList<>();
    final Pattern piPattern = Pattern.compile("PI_(\\d*)$");
    Arrays.stream(treePath.split("/"))
        .map(piPattern::matcher)
        .filter(Matcher::matches)
        .forEach(matcher -> processInstanceIds.add(matcher.group(1)));

    return processInstanceIds;
  }
}
