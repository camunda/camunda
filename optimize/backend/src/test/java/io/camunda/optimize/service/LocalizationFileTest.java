/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class LocalizationFileTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void localizationFilesHaveTheSameKeys() {
    // given
    final String enLocale = "en";
    final String deLocale = "de";

    // when
    final List<String> enKeys =
        buildQualifiedKeyList(
            getJsonTreeMapFromLocalizationFile(enLocale), Lists.newArrayList(), null);
    final List<String> deKeys =
        buildQualifiedKeyList(
            getJsonTreeMapFromLocalizationFile(deLocale), Lists.newArrayList(), null);

    // then
    assertThat(enKeys).containsExactlyInAnyOrderElementsOf(deKeys);
  }

  private List<String> buildQualifiedKeyList(
      final Map<String, Object> jsonMap, final List<String> keys, final String parentKeyPath) {
    jsonMap.forEach(
        (key, value) -> {
          final String qualifiedKeyPath = Optional.ofNullable(parentKeyPath).orElse("") + "/" + key;
          if (value instanceof LinkedHashMap) {
            final Map<String, Object> map = (LinkedHashMap) value;
            buildQualifiedKeyList(map, keys, qualifiedKeyPath);
          }
          keys.add(qualifiedKeyPath);
        });
    return keys;
  }

  private Map<String, Object> getJsonTreeMapFromLocalizationFile(final String locale) {
    try {
      return OBJECT_MAPPER.readValue(
          getClass()
              .getClassLoader()
              .getResourceAsStream(LocalizationService.LOCALIZATION_PATH + locale + ".json"),
          Map.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }
}
