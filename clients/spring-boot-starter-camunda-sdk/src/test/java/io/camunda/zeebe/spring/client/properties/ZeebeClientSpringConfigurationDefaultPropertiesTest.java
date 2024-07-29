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
package io.camunda.zeebe.spring.client.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
    classes = ZeebeClientSpringConfigurationDefaultPropertiesTest.TestConfig.class)
public class ZeebeClientSpringConfigurationDefaultPropertiesTest {

  @Autowired private ZeebeClientConfigurationProperties properties;

  @Test
  public void hasRequestTimeout() {
    assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  public void hasNoWorkerName() {
    assertThat(properties.getDefaultJobWorkerName()).isNull();
  }

  @Test
  public void hasJobTimeout() {
    assertThat(properties.getDefaultJobTimeout()).isEqualTo(Duration.ofSeconds(300));
  }

  @Test
  public void hasWorkerMaxJobsActive() {
    assertThat(properties.getDefaultJobWorkerMaxJobsActive()).isEqualTo(32);
  }

  @Test
  public void hasJobPollInterval() {
    assertThat(properties.getDefaultJobPollInterval()).isEqualTo(Duration.ofNanos(100000000));
  }

  @Test
  public void hasWorkerThreads() {
    assertThat(properties.getNumJobWorkerExecutionThreads()).isEqualTo(1);
  }

  @Test
  public void hasMessageTimeToLeave() {
    assertThat(properties.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofSeconds(3600));
  }

  @Test
  public void hasSecurityCertificatePath() {
    assertThat(properties.getCaCertificatePath()).isNull();
  }

  @EnableConfigurationProperties(ZeebeClientConfigurationProperties.class)
  public static class TestConfig {}
}
