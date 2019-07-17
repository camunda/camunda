/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import com.google.common.collect.ImmutableList;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.ReportConstants;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@UtilityClass
public class DefinitionVersionHandlingUtil {

  public static String convertToValidDefinitionVersion(String processDefinitionKey,
                                                       @NonNull List<String> processDefinitionVersions,
                                                       Function<String, String> getLatestVersionToKey) {
    Optional<String> isDefinitionVersionSetToAll = processDefinitionVersions.stream()
      .filter(ReportConstants.ALL_VERSIONS::equalsIgnoreCase)
      .findFirst();
    if (isDefinitionVersionSetToAll.isPresent()) {
      return getLatestVersionToKey.apply(processDefinitionKey);
    } else {
      return processDefinitionVersions.stream()
        .filter(StringUtils::isNumeric)
        .map(Integer::parseInt)
        .max(Integer::compareTo)
        .map(Object::toString)
        .orElse(getLastEntryInList(processDefinitionVersions));
    }
  }

  public static String convertToValidVersion(String processDefinitionKey,
                                       String processDefinitionVersion,
                                       Function<String, String> getLatestVersionToKey) {
    return convertToValidDefinitionVersion(
      processDefinitionKey,
      ImmutableList.of(processDefinitionVersion),
      getLatestVersionToKey
    );
  }


  private static String getLastEntryInList(@NonNull List<String> processDefinitionVersions) {
    return processDefinitionVersions.get(processDefinitionVersions.size() - 1);
  }
}
