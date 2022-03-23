/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import com.google.common.collect.ImmutableList;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.ReportConstants;

import java.util.Collections;
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
    final boolean isDefinitionVersionSetToAllOrLatest = definitionVersions.stream()
      .anyMatch(
        version -> ReportConstants.ALL_VERSIONS.equalsIgnoreCase(version) ||
          ReportConstants.LATEST_VERSION.equalsIgnoreCase(version)
      );
    if (isDefinitionVersionSetToAllOrLatest) {
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

  private static String getLastEntryInList(@NonNull final List<String> processDefinitionVersions) {
    return processDefinitionVersions.get(processDefinitionVersions.size() - 1);
  }

  public static boolean isDefinitionVersionSetToAll(final List<String> definitionVersions) {
    Optional<String> allVersionSelected = definitionVersions.stream()
      .filter(ReportConstants.ALL_VERSIONS::equalsIgnoreCase)
      .findFirst();
    return allVersionSelected.isPresent();
  }

  public static boolean isDefinitionVersionSetToLatest(final List<String> definitionVersions) {
    Optional<String> allVersionSelected = definitionVersions.stream()
      .filter(ReportConstants.LATEST_VERSION::equalsIgnoreCase)
      .findFirst();
    return allVersionSelected.isPresent();
  }

  public static boolean isDefinitionVersionSetToAllOrLatest(final String definitionVersion) {
    return isDefinitionVersionSetToAllOrLatest(Collections.singletonList(definitionVersion));
  }

  public static boolean isDefinitionVersionSetToAllOrLatest(final List<String> definitionVersions) {
    return definitionVersions.stream()
      .anyMatch(v -> ReportConstants.ALL_VERSIONS.equalsIgnoreCase(v)
        || ReportConstants.LATEST_VERSION.equalsIgnoreCase(v));
  }

}
