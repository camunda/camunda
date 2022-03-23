/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;

import java.util.List;
import java.util.Optional;

public class MapResultUtil {
  public static Optional<MapResultEntryDto> getEntryForKey(List<MapResultEntryDto> mapResult, String key) {
    return mapResult.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  public static Optional<HyperMapResultEntryDto> getDataEntryForKey(List<HyperMapResultEntryDto> mapResult, String key) {
    return mapResult.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
  }

  public static Optional<MapResultEntryDto> getDataEntryForKey(HyperMapResultEntryDto hyperMapEntry, String key) {
    return getEntryForKey(hyperMapEntry.getValue(), key);
  }
}
