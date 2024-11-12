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
package io.camunda.process.test.impl.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ContainerRuntimeVersionUtilTest {

  @Test
  void shouldReturnDefaults() {
    // given
    final Properties versionProperties = new Properties();

    // when
    final ContainerRuntimeVersionUtil versionUtil =
        new ContainerRuntimeVersionUtil(versionProperties);

    // then
    assertThat(versionUtil.getCamundaVersion()).isEqualTo("SNAPSHOT");
    assertThat(versionUtil.getElasticsearchVersion()).isEqualTo("8.13.0");
  }

  @Test
  void shouldReturnDefaultsForDevelopment() {
    // given
    final Properties versionProperties = new Properties();
    versionProperties.put(
        ContainerRuntimeVersionUtil.PROPERTY_NAME_CAMUNDA_VERSION, "${project.version}");
    versionProperties.put(
        ContainerRuntimeVersionUtil.PROPERTY_NAME_ELASTICSEARCH_VERSION,
        "${version.elasticsearch}");

    // when
    final ContainerRuntimeVersionUtil versionUtil =
        new ContainerRuntimeVersionUtil(versionProperties);

    // then
    assertThat(versionUtil.getCamundaVersion()).isEqualTo("SNAPSHOT");
    assertThat(versionUtil.getElasticsearchVersion()).isEqualTo("8.13.0");
  }

  @ParameterizedTest
  @CsvSource({
    "8.6.0, 8.6.0",
    "8.6.1, 8.6.1",
    "8.6.0-SNAPSHOT, SNAPSHOT",
    "8.5.2-SNAPSHOT, 8.5.1",
    "8.5.2-rc1, 8.5.1",
  })
  void shouldReturnCamundaVersion(final String propertyVersion, final String expectedVersion) {
    // given
    final Properties versionProperties = new Properties();
    versionProperties.put(
        ContainerRuntimeVersionUtil.PROPERTY_NAME_CAMUNDA_VERSION, propertyVersion);

    // when
    final ContainerRuntimeVersionUtil versionUtil =
        new ContainerRuntimeVersionUtil(versionProperties);

    // then
    assertThat(versionUtil.getCamundaVersion()).isEqualTo(expectedVersion);
  }

  @Test
  void shouldReturnElasticsearchVersion() {
    // given
    final Properties versionProperties = new Properties();
    versionProperties.put(
        ContainerRuntimeVersionUtil.PROPERTY_NAME_ELASTICSEARCH_VERSION, "8.13.1");

    // when
    final ContainerRuntimeVersionUtil versionUtil =
        new ContainerRuntimeVersionUtil(versionProperties);

    // then
    assertThat(versionUtil.getElasticsearchVersion()).isEqualTo("8.13.1");
  }
}
