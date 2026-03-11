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
}
