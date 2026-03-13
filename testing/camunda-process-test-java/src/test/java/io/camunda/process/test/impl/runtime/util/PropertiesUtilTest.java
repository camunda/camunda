/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.runtime.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PropertiesUtilTest {

  @ParameterizedTest
  @CsvSource({
    "judge.threshold,JUDGE_THRESHOLD",
    "judge.customPrompt,JUDGE_CUSTOMPROMPT",
    "judge.chatModel.provider,JUDGE_CHATMODEL_PROVIDER",
    "judge.chatModel.apiKey,JUDGE_CHATMODEL_APIKEY",
    "judge.chatModel.baseUrl,JUDGE_CHATMODEL_BASEURL",
    "judge.chatModel.region,JUDGE_CHATMODEL_REGION",
    "judge.chatModel.credentials.accessKey,JUDGE_CHATMODEL_CREDENTIALS_ACCESSKEY",
    "judge.chatModel.credentials.secretKey,JUDGE_CHATMODEL_CREDENTIALS_SECRETKEY",
    "some.kebab-case.property,SOME_KEBABCASE_PROPERTY",
  })
  void shouldConvertPropertyNameToEnvVarName(
      final String propertyName, final String expectedEnvVar) {
    assertThat(PropertiesUtil.toEnvVarName(propertyName)).isEqualTo(expectedEnvVar);
  }

  @Nested
  class GetPropertyMapOrEmpty {

    @Test
    void shouldReturnEmptyMapWhenNoMatchingProperties() {
      final Properties props = new Properties();
      props.setProperty("other.key", "value");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props, "prefix", Function.identity(), Collections.emptyMap());

      assertThat(result).isEmpty();
    }

    @Test
    void shouldCollectPropertiesWithMatchingPrefix() {
      final Properties props = new Properties();
      props.setProperty("judge.chatModel.customProperties.endpoint", "http://localhost:8080");
      props.setProperty("judge.chatModel.customProperties.temperature", "0.7");
      props.setProperty("judge.chatModel.provider", "openai");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props,
              "judge.chatModel.customProperties",
              Function.identity(),
              Collections.emptyMap());

      assertThat(result)
          .containsEntry("endpoint", "http://localhost:8080")
          .containsEntry("temperature", "0.7")
          .hasSize(2);
    }

    @Test
    void shouldHandlePrefixWithTrailingDot() {
      final Properties props = new Properties();
      props.setProperty("prefix.key", "value");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props, "prefix.", Function.identity(), Collections.emptyMap());

      assertThat(result).containsEntry("key", "value").hasSize(1);
    }

    @Test
    void shouldResolveEnvVarWhenPropertyValueIsPlaceholder() {
      final Properties props = new Properties();
      props.setProperty("judge.chatModel.customProperties.endpoint", "${CUSTOM_ENDPOINT}");

      final Map<String, String> envVars =
          Collections.singletonMap(
              "JUDGE_CHATMODEL_CUSTOMPROPERTIES_ENDPOINT", "http://env-resolved:9090");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props, "judge.chatModel.customProperties", Function.identity(), envVars);

      assertThat(result).containsEntry("endpoint", "http://env-resolved:9090").hasSize(1);
    }

    @Test
    void shouldOmitEntryWhenPlaceholderAndNoMatchingEnvVar() {
      final Properties props = new Properties();
      props.setProperty("judge.chatModel.customProperties.endpoint", "${CUSTOM_ENDPOINT}");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props,
              "judge.chatModel.customProperties",
              Function.identity(),
              Collections.emptyMap());

      assertThat(result).isEmpty();
    }

    @Test
    void shouldDiscoverEntriesFromEnvVarsMatchingPrefix() {
      final Properties props = new Properties();

      final Map<String, String> envVars = new HashMap<>();
      envVars.put("JUDGE_CHATMODEL_CUSTOMPROPERTIES_ENDPOINT", "http://discovered:8080");
      envVars.put("JUDGE_CHATMODEL_CUSTOMPROPERTIES_TEMPERATURE", "0.9");
      envVars.put("JUDGE_CHATMODEL_PROVIDER", "openai");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props, "judge.chatModel.customProperties", Function.identity(), envVars);

      assertThat(result)
          .containsEntry("endpoint", "http://discovered:8080")
          .containsEntry("temperature", "0.9")
          .hasSize(2);
    }

    @Test
    void shouldLowercaseEnvVarDiscoveredKeys() {
      final Properties props = new Properties();

      final Map<String, String> envVars =
          Collections.singletonMap("JUDGE_CHATMODEL_CUSTOMPROPERTIES_MYKEY", "value");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props, "judge.chatModel.customProperties", Function.identity(), envVars);

      assertThat(result).containsEntry("mykey", "value").hasSize(1);
    }

    @Test
    void shouldPreferPropertyValueOverEnvVarDiscovery() {
      final Properties props = new Properties();
      props.setProperty("judge.chatModel.customProperties.endpoint", "http://from-props:8080");

      final Map<String, String> envVars =
          Collections.singletonMap(
              "JUDGE_CHATMODEL_CUSTOMPROPERTIES_ENDPOINT", "http://from-env:9090");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props, "judge.chatModel.customProperties", Function.identity(), envVars);

      assertThat(result).containsEntry("endpoint", "http://from-props:8080").hasSize(1);
    }

    @Test
    void shouldCombinePropertiesAndEnvVarDiscoveredEntries() {
      final Properties props = new Properties();
      props.setProperty("judge.chatModel.customProperties.endpoint", "http://from-props:8080");

      final Map<String, String> envVars =
          Collections.singletonMap("JUDGE_CHATMODEL_CUSTOMPROPERTIES_TIMEOUT", "30");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props, "judge.chatModel.customProperties", Function.identity(), envVars);

      assertThat(result)
          .containsEntry("endpoint", "http://from-props:8080")
          .containsEntry("timeout", "30")
          .hasSize(2);
    }

    @Test
    void shouldApplyConverterToAllResolvedValues() {
      final Properties props = new Properties();
      props.setProperty("prefix.a", "123");

      final Map<String, String> envVars = Collections.singletonMap("PREFIX_B", "456");

      final Map<String, Integer> result =
          PropertiesUtil.getPropertyMapOrEmpty(props, "prefix", Integer::parseInt, envVars);

      assertThat(result).containsEntry("a", 123).containsEntry("b", 456).hasSize(2);
    }

    @Test
    void shouldNotDiscoverEnvVarWithEmptySuffix() {
      final Properties props = new Properties();

      final Map<String, String> envVars =
          Collections.singletonMap("JUDGE_CHATMODEL_CUSTOMPROPERTIES_", "should-not-appear");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(
              props, "judge.chatModel.customProperties", Function.identity(), envVars);

      assertThat(result).isEmpty();
    }

    @Test
    void shouldIgnoreEmptyEnvVarValuesForPlaceholders() {
      final Properties props = new Properties();
      props.setProperty("prefix.key", "${PLACEHOLDER}");

      final Map<String, String> envVars = Collections.singletonMap("PREFIX_KEY", "");

      final Map<String, String> result =
          PropertiesUtil.getPropertyMapOrEmpty(props, "prefix", Function.identity(), envVars);

      assertThat(result).isEmpty();
    }
  }
}
