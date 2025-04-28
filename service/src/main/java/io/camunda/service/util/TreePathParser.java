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

/**
 * Temporary workaround for parsing treePath strings within the service module.
 *
 * <p>The real implementation should eventually live in a shared module within Camunda 8, which
 * provides centralized access to TreePath logic for all modules that need it (e.g., service,
 * webapp, exporter, importer).
 *
 * <p>This class should be removed once a proper <a
 * href="https://github.com/camunda/camunda/issues/31218">shared utility or domain module</a> is
 * established.
 */
public final class TreePathParser {

  private static final String PROCESS_INSTANCE_PATTERN = "PI_(\\d*)$";

  private TreePathParser() {}

  public static List<Long> extractProcessInstanceKeys(final String treePath) {
    final List<Long> processInstanceIds = new ArrayList<>();
    final Pattern piPattern = Pattern.compile(PROCESS_INSTANCE_PATTERN);
    Arrays.stream(treePath.split("/"))
        .map(piPattern::matcher)
        .filter(Matcher::matches)
        .forEach(matcher -> processInstanceIds.add(Long.valueOf(matcher.group(1))));

    return processInstanceIds;
  }
}
