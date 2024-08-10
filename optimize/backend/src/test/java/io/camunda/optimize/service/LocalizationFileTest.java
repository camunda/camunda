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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class LocalizationFileTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void localizationFilesHaveTheSameKeys() {
    // given
    String enLocale = "en";
    String deLocale = "de";

    // when
    List<String> enKeys =
        buildQualifiedKeyList(
            getJsonTreeMapFromLocalizationFile(enLocale), Lists.newArrayList(), null);
    List<String> deKeys =
        buildQualifiedKeyList(
            getJsonTreeMapFromLocalizationFile(deLocale), Lists.newArrayList(), null);

    // then
    assertThat(enKeys).containsExactlyInAnyOrderElementsOf(deKeys);
  }

  private List<String> buildQualifiedKeyList(
      Map<String, Object> jsonMap, List<String> keys, String parentKeyPath) {
    jsonMap.forEach(
        (key, value) -> {
          String qualifiedKeyPath = Optional.ofNullable(parentKeyPath).orElse("") + "/" + key;
          if (value instanceof LinkedHashMap) {
            Map<String, Object> map = (LinkedHashMap) value;
            buildQualifiedKeyList(map, keys, qualifiedKeyPath);
          }
          keys.add(qualifiedKeyPath);
        });
    return keys;
  }

  @SneakyThrows
  private Map<String, Object> getJsonTreeMapFromLocalizationFile(final String locale) {
    return OBJECT_MAPPER.readValue(
        getClass()
            .getClassLoader()
            .getResourceAsStream(LocalizationService.LOCALIZATION_PATH + locale + ".json"),
        Map.class);
  }
}
