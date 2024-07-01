/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.report.util;
//
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
// import java.util.List;
// import java.util.Optional;
//
// public class MapResultUtil {
//   public static Optional<MapResultEntryDto> getEntryForKey(
//       List<MapResultEntryDto> mapResult, String key) {
//     return mapResult.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
//   }
//
//   public static Optional<HyperMapResultEntryDto> getDataEntryForKey(
//       List<HyperMapResultEntryDto> mapResult, String key) {
//     return mapResult.stream().filter(entry -> key.equals(entry.getKey())).findFirst();
//   }
//
//   public static Optional<MapResultEntryDto> getDataEntryForKey(
//       HyperMapResultEntryDto hyperMapEntry, String key) {
//     return getEntryForKey(hyperMapEntry.getValue(), key);
//   }
// }
