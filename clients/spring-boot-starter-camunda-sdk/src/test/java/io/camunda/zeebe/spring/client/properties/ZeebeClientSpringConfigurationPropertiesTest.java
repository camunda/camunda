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

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@TestPropertySource(
    properties = {
      "zeebe.client.broker.gatewayAddress=localhost12345",
      "zeebe.client.broker.grpcAddress=https://localhost:1234",
      "zeebe.client.broker.restAddress=https://localhost:8080",
      "zeebe.client.requestTimeout=99s",
      "zeebe.client.job.timeout=99s",
      "zeebe.client.job.pollInterval=99s",
      "zeebe.client.worker.maxJobsActive=99",
      "zeebe.client.worker.threads=99",
      "zeebe.client.worker.defaultName=testName",
      "zeebe.client.worker.defaultType=testType",
      "zeebe.client.worker.override.foo.enabled=false",
      "zeebe.client.message.timeToLive=99s",
      "zeebe.client.security.certpath=aPath",
      "zeebe.client.security.plaintext=true"
    })
@ContextConfiguration(classes = ZeebeClientSpringConfigurationPropertiesTest.TestConfig.class)
public class ZeebeClientSpringConfigurationPropertiesTest {

  @Autowired private ZeebeClientConfigurationProperties properties;

  @Test
  public void hasDeprecatedGatewayAddress() {
    assertThat(properties.getGatewayAddress()).isEqualTo("localhost12345");
  }

  @Test
  public void hasGrpcAddress() {
    assertThat(properties.getGrpcAddress().toString()).isEqualTo("https://localhost:1234");
  }

  @Test
  public void hasRestAddress() {
    assertThat(properties.getRestAddress().toString()).isEqualTo("https://localhost:8080");
  }

  @Test
  public void hasRequestTimeout() {
    assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(99));
  }

  @Test
  public void hasWorkerName() {
    assertThat(properties.getDefaultJobWorkerName()).isEqualTo("testName");
  }

  @Test
  public void hasWorkerType() {
    assertThat(properties.getDefaultJobWorkerType()).isEqualTo("testType");
  }

  @Test
  public void hasJobTimeout() {
    assertThat(properties.getJob().getTimeout()).isEqualTo(Duration.ofSeconds(99));
  }

  @Test
  public void hasWorkerMaxJobsActive() {
    assertThat(properties.getWorker().getMaxJobsActive()).isEqualTo(99);
  }

  @Test
  public void hasJobPollInterval() {
    assertThat(properties.getJob().getPollInterval()).isEqualTo(Duration.ofSeconds(99));
  }

  @Test
  public void hasWorkerThreads() {
    assertThat(properties.getWorker().getThreads()).isEqualTo(99);
  }

  @Test
  public void hasMessageTimeToLeave() {
    assertThat(properties.getMessage().getTimeToLive()).isEqualTo(Duration.ofSeconds(99));
  }

  @Test
  public void isSecurityPlainTextDisabled() {
    assertThat(properties.getSecurity().isPlaintext()).isTrue();
  }

  @Test
  public void hasSecurityCertificatePath() {
    assertThat(properties.getSecurity().getCertPath()).isEqualTo("aPath");
  }

  @Test
  void shouldFooWorkerDisabled() {
    assertThat(properties.getWorker().getOverride().get("foo").getEnabled()).isFalse();
  }

  @EnableConfigurationProperties(ZeebeClientConfigurationProperties.class)
  public static class TestConfig {
    @Bean("jsonMapper")
    @ConditionalOnMissingBean(JsonMapper.class)
    public JsonMapper jsonMapper() {
      return new ZeebeObjectMapper();
    }
  }
}
