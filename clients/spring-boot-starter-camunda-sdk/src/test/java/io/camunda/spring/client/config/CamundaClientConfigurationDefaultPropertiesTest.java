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
package io.camunda.spring.client.config;

import static io.camunda.client.impl.util.DataSizeUtil.ONE_KB;
import static io.camunda.client.impl.util.DataSizeUtil.ONE_MB;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.spring.client.config.legacy.CamundaClientStarterAutoConfigurationTest;
import io.camunda.spring.client.configuration.CamundaAutoConfiguration;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(
    classes = {
      CamundaAutoConfiguration.class,
      CamundaClientStarterAutoConfigurationTest.TestConfig.class
    })
public class CamundaClientConfigurationDefaultPropertiesTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void testDefaultClientConfiguration() throws URISyntaxException {
    final CamundaClientConfiguration configuration =
        applicationContext.getBean(CamundaClientConfiguration.class);

    assertThat(configuration.isPlaintextConnectionEnabled()).isTrue();
    assertThat(configuration.getCaCertificatePath()).isNull();
    assertThat(configuration.getCredentialsProvider()).isInstanceOf(NoopCredentialsProvider.class);
    assertThat(configuration.getDefaultJobPollInterval()).isEqualTo(Duration.ofMillis(100));
    assertThat(configuration.getDefaultJobTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(configuration.getDefaultJobWorkerMaxJobsActive()).isEqualTo(32);
    assertThat(configuration.getDefaultJobWorkerName()).isEqualTo("default");
    assertThat(configuration.getDefaultJobWorkerStreamEnabled()).isFalse();
    assertThat(configuration.getDefaultJobWorkerTenantIds())
        .isEqualTo(Collections.singletonList("<default>"));
    assertThat(configuration.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofHours(1));
    assertThat(configuration.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(configuration.getDefaultTenantId()).isEqualTo("<default>");
    assertThat(configuration.getGatewayAddress()).isEqualTo("0.0.0.0:26500");
    assertThat(configuration.getGrpcAddress()).isEqualTo(new URI("http://0.0.0.0:26500"));
    assertThat(configuration.getKeepAlive()).isEqualTo(Duration.ofSeconds(45));
    assertThat(configuration.getMaxMessageSize()).isEqualTo(5 * ONE_MB);
    assertThat(configuration.getMaxMetadataSize()).isEqualTo(16 * ONE_KB);
    assertThat(configuration.getNumJobWorkerExecutionThreads()).isEqualTo(1);
    assertThat(configuration.getOverrideAuthority()).isNull();
    assertThat(configuration.getRestAddress()).isEqualTo(new URI("http://0.0.0.0:8080"));
    assertThat(configuration.preferRestOverGrpc()).isFalse();
  }
}
