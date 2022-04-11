/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalizationFileTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void localizationFilesHaveTheSameKeys() {
    // given
    String enLocale = "en";
    String deLocale = "de";

    // when
    List<String> enKeys = buildQualifiedKeyList(
      getJsonTreeMapFromLocalizationFile(enLocale),
      Lists.newArrayList(),
      null
    );
    List<String> deKeys = buildQualifiedKeyList(
      getJsonTreeMapFromLocalizationFile(deLocale),
      Lists.newArrayList(),
      null
    );

    // then
    assertThat(enKeys).containsExactlyInAnyOrderElementsOf(deKeys);
  }

  private List<String> buildQualifiedKeyList(Map<String, Object> jsonMap, List<String> keys, String parentKeyPath) {
    jsonMap.forEach((key, value) -> {
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
      getClass().getClassLoader()
        .getResourceAsStream(LocalizationService.LOCALIZATION_PATH + locale + ".json"),
      Map.class
    );
  }

}
