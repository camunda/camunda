/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

public class DefinitionVersionHandlingUtil {

  private DefinitionVersionHandlingUtil() {}

  public static String convertToLatestParticularVersion(
      final String processDefinitionVersion, final Supplier<String> latestVersionSupplier) {
    return convertToLatestParticularVersion(
        ImmutableList.of(processDefinitionVersion), latestVersionSupplier);
  }

  public static String convertToLatestParticularVersion(
      final List<String> definitionVersions, final Supplier<String> latestVersionSupplier) {
    if (definitionVersions == null) {
      throw new IllegalArgumentException("definitionVersions cannot be null");
    }

    if (latestVersionSupplier == null) {
      throw new IllegalArgumentException("latestVersionSupplier cannot be null");
    }

    final boolean isDefinitionVersionSetToAllOrLatest =
        definitionVersions.stream()
            .anyMatch(
                version ->
                    ReportConstants.ALL_VERSIONS.equalsIgnoreCase(version)
                        || ReportConstants.LATEST_VERSION.equalsIgnoreCase(version));
    if (isDefinitionVersionSetToAllOrLatest) {
      return latestVersionSupplier.get();
    } else {
      try {
        return definitionVersions.stream()
            .filter(StringUtils::isNumeric)
            .map(Integer::parseInt)
            .max(Integer::compareTo)
            .map(Object::toString)
            .orElse(getLastEntryInList(definitionVersions));
      } catch (final NumberFormatException exception) {
        throw new OptimizeRuntimeException("Cannot determine latest version for definition");
      }
    }
  }

  private static String getLastEntryInList(final List<String> processDefinitionVersions) {
    return processDefinitionVersions.get(processDefinitionVersions.size() - 1);
  }

  public static boolean isDefinitionVersionSetToAll(final List<String> definitionVersions) {
    return definitionVersions.stream().anyMatch(ReportConstants.ALL_VERSIONS::equalsIgnoreCase);
  }

  public static boolean isDefinitionVersionSetToLatest(final List<String> definitionVersions) {
    return definitionVersions.stream().anyMatch(ReportConstants.LATEST_VERSION::equalsIgnoreCase);
  }

  public static boolean isDefinitionVersionSetToAllOrLatest(final String definitionVersion) {
    return isDefinitionVersionSetToAllOrLatest(Collections.singletonList(definitionVersion));
  }

  public static boolean isDefinitionVersionSetToAllOrLatest(final List<String> definitionVersions) {
    return definitionVersions.stream()
        .anyMatch(
            v ->
                ReportConstants.ALL_VERSIONS.equalsIgnoreCase(v)
                    || ReportConstants.LATEST_VERSION.equalsIgnoreCase(v));
  }
}
