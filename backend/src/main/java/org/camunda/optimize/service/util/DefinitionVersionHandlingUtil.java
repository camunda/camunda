/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import com.google.common.collect.ImmutableList;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.ReportConstants;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DefinitionVersionHandlingUtil {

  public static String convertToLatestParticularVersion(final String processDefinitionVersion,
                                                        final Supplier<String> latestVersionSupplier) {
    return convertToLatestParticularVersion(ImmutableList.of(processDefinitionVersion), latestVersionSupplier);
  }

  public static String convertToLatestParticularVersion(@NonNull final List<String> definitionVersions,
                                                        @NonNull final Supplier<String> latestVersionSupplier) {
    Optional<String> isDefinitionVersionSetToAllOrLatest = definitionVersions.stream()
      .filter(
        version -> ReportConstants.ALL_VERSIONS.equalsIgnoreCase(version) ||
          ReportConstants.LATEST_VERSION.equalsIgnoreCase(version)
      )
      .findFirst();
    if (isDefinitionVersionSetToAllOrLatest.isPresent()) {
      return latestVersionSupplier.get();
    } else {
      return definitionVersions.stream()
        .filter(StringUtils::isNumeric)
        .map(Integer::parseInt)
        .max(Integer::compareTo)
        .map(Object::toString)
        .orElse(getLastEntryInList(definitionVersions));
    }
  }

  private static String getLastEntryInList(@NonNull List<String> processDefinitionVersions) {
    return processDefinitionVersions.get(processDefinitionVersions.size() - 1);
  }

  public static boolean isDefinitionVersionSetToAll(List<String> definitionVersions) {
    Optional<String> allVersionSelected = definitionVersions.stream()
      .filter(ReportConstants.ALL_VERSIONS::equalsIgnoreCase)
      .findFirst();
    return allVersionSelected.isPresent();
  }

  public static boolean isDefinitionVersionSetToLatest(List<String> definitionVersions) {
    Optional<String> allVersionSelected = definitionVersions.stream()
      .filter(ReportConstants.LATEST_VERSION::equalsIgnoreCase)
      .findFirst();
    return allVersionSelected.isPresent();
  }

  public static boolean isDefinitionVersionSetToAllOrLatest(List<String> definitionVersions) {
    return definitionVersions.stream()
      .anyMatch(v -> v.equalsIgnoreCase(ReportConstants.ALL_VERSIONS) || v.equalsIgnoreCase(ReportConstants.LATEST_VERSION));
  }

}
