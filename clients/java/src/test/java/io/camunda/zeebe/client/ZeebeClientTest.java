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
package io.camunda.zeebe.client;

import static io.camunda.zeebe.client.ClientProperties.CLOUD_REGION;
import static io.camunda.zeebe.client.ClientProperties.MAX_MESSAGE_SIZE;
import static io.camunda.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION;
import static io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl.CA_CERTIFICATE_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl.KEEP_ALIVE_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl.OVERRIDE_AUTHORITY_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl.PLAINTEXT_CONNECTION_VAR;
import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_MB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.impl.NoopCredentialsProvider;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.client.impl.util.Environment;
import io.camunda.zeebe.client.impl.util.EnvironmentRule;
import io.camunda.zeebe.client.util.ClientTest;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class ZeebeClientTest extends ClientTest {
  @Rule public final EnvironmentRule environmentRule = new EnvironmentRule();
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void shouldNotFailIfClosedTwice() {
    client.close();
    client.close();
  }

  @Test
  public void shouldHaveDefaultValues() {
    // given
    try (final ZeebeClient client = ZeebeClient.newClient()) {
      // when
      final ZeebeClientConfiguration configuration = client.getConfiguration();

      // then
      assertThat(configuration.getGatewayAddress()).isEqualTo("0.0.0.0:26500");
      assertThat(configuration.getDefaultJobWorkerMaxJobsActive()).isEqualTo(32);
      assertThat(configuration.getNumJobWorkerExecutionThreads()).isEqualTo(1);
      assertThat(configuration.getDefaultJobWorkerName()).isEqualTo("default");
      assertThat(configuration.getDefaultJobTimeout()).isEqualTo(Duration.ofMinutes(5));
      assertThat(configuration.getDefaultJobPollInterval()).isEqualTo(Duration.ofMillis(100));
      assertThat(configuration.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofHours(1));
      assertThat(configuration.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
      assertThat(configuration.getMaxMessageSize()).isEqualTo(4 * 1024 * 1024);
      assertThat(configuration.getOverrideAuthority()).isNull();
    }
  }

  @Test
  public void shouldFailIfCertificateDoesNotExist() {
    assertThatThrownBy(
            () -> ZeebeClient.newClientBuilder().caCertificatePath("/wrong/path").build())
        .hasCauseInstanceOf(FileNotFoundException.class);
  }

  @Test
  public void shouldFailWithEmptyCertificatePath() {
    assertThatThrownBy(() -> ZeebeClient.newClientBuilder().caCertificatePath("").build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldHaveTlsEnabledByDefault() {
    assertThat(new ZeebeClientBuilderImpl().isPlaintextConnectionEnabled()).isFalse();
  }

  @Test
  public void shouldUseInsecureWithEnvVar() {
    // given
    Environment.system().put(PLAINTEXT_CONNECTION_VAR, "true");
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    // when
    builder.build();

    // then
    assertThat(builder.isPlaintextConnectionEnabled()).isTrue();
  }

  @Test
  public void shouldOverridePropertyWithEnvVariable() {
    // given
    Environment.system().put(PLAINTEXT_CONNECTION_VAR, "false");
    final Properties properties = new Properties();
    properties.putIfAbsent(USE_PLAINTEXT_CONNECTION, "true");
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.isPlaintextConnectionEnabled()).isFalse();
  }

  @Test
  public void shouldNotOverridePropertyWithEnvVariableIfOverridingIsDisabled() {
    // given
    Environment.system().put(PLAINTEXT_CONNECTION_VAR, "false");
    final Properties properties = new Properties();
    properties.putIfAbsent(USE_PLAINTEXT_CONNECTION, "true");
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.applyEnvironmentVariableOverrides(false);
    builder.withProperties(properties);

    // when
    builder.build();

    // then
    assertThat(builder.isPlaintextConnectionEnabled()).isTrue();
  }

  @Test
  public void shouldCaCertificateWithEnvVar() {
    // given
    final String certPath = getClass().getClassLoader().getResource("ca.cert.pem").getPath();
    Environment.system().put(CA_CERTIFICATE_VAR, certPath);
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    // when
    builder.build();

    // then
    assertThat(builder.getCaCertificatePath()).isEqualTo(certPath);
  }

  @Test
  public void shouldSetKeepAlive() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.keepAlive(Duration.ofMinutes(2));

    // when
    builder.build();

    // then
    assertThat(builder.getKeepAlive()).isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  public void shouldOverrideKeepAliveWithEnvVar() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.keepAlive(Duration.ofMinutes(2));
    Environment.system().put(KEEP_ALIVE_VAR, "15000");

    // when
    builder.build();

    // then
    assertThat(builder.getKeepAlive()).isEqualTo(Duration.ofSeconds(15));
  }

  @Test
  public void shouldSetAuthority() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.overrideAuthority("virtualhost");

    // when
    builder.build();

    // then
    assertThat(builder.getOverrideAuthority()).isEqualTo("virtualhost");
  }

  @Test
  public void shouldOverrideAuthorityWithEnvVar() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.overrideAuthority("localhost");
    Environment.system().put(OVERRIDE_AUTHORITY_VAR, "virtualhost");

    // when
    builder.build();

    // then
    assertThat(builder.getOverrideAuthority()).isEqualTo("virtualhost");
  }

  @Test
  public void shouldSetMaxMessageSize() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.maxMessageSize(10 * 1024 * 1024);

    // when
    builder.build();

    // then
    assertThat(builder.getMaxMessageSize()).isEqualTo(10 * 1024 * 1024);
  }

  @Test
  public void shouldSetMaxMessageSizeWithProperty() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    final Properties properties = new Properties();
    properties.setProperty(MAX_MESSAGE_SIZE, "10MB");
    builder.withProperties(properties);
    // when
    builder.build();

    // then
    assertThat(builder.getMaxMessageSize()).isEqualTo(10 * ONE_MB);
  }

  @Test
  public void shouldOverrideMaxMessageSizeWithEnvVar() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.applyEnvironmentVariableOverrides(Boolean.TRUE);
    builder.maxMessageSize(4 * ONE_MB);
    Environment.system().put(MAX_MESSAGE_SIZE, "10MB");

    // when
    builder.build();

    // then
    assertThat(builder.getMaxMessageSize()).isEqualTo(10 * ONE_MB);
  }

  @Test
  public void shouldRejectUnsupportedTimeUnitWithEnvVar() {
    // when/then
    Environment.system().put(KEEP_ALIVE_VAR, "30d");
    assertThatThrownBy(() -> new ZeebeClientBuilderImpl().build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectNegativeTime() {
    // when/then
    assertThatThrownBy(() -> new ZeebeClientBuilderImpl().keepAlive(Duration.ofSeconds(-2)).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectNegativeTimeAsEnvVar() {
    // when/then
    Environment.system().put(KEEP_ALIVE_VAR, "-2s");
    assertThatThrownBy(() -> new ZeebeClientBuilderImpl().build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldCloudBuilderBuildProperClient() {
    // given
    final String clusterId = "clusterId";
    final String region = "asdf-123";

    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId(clusterId)
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .withRegion(region)
            .build()) {
      // when
      final ZeebeClientConfiguration clientConfiguration = client.getConfiguration();
      // then
      assertThat(clientConfiguration.getCredentialsProvider())
          .isInstanceOf(OAuthCredentialsProvider.class);
      assertThat(clientConfiguration.getGatewayAddress())
          .isEqualTo(String.format("%s.%s.zeebe.camunda.io:443", clusterId, region));
    }
  }

  @Test
  public void shouldCloudBuilderBuildProperClientWithDefaultRegion() {
    // given
    final String clusterId = "clusterId";
    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId(clusterId)
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .build()) {
      // when
      final ZeebeClientConfiguration clientConfiguration = client.getConfiguration();
      // then
      assertThat(clientConfiguration.getCredentialsProvider())
          .isInstanceOf(OAuthCredentialsProvider.class);
      assertThat(clientConfiguration.getGatewayAddress())
          .isEqualTo(String.format("%s.bru-2.zeebe.camunda.io:443", clusterId));
    }
  }

  @Test
  public void shouldOverrideCloudProperties() {
    // given
    final String gatewayAddress = "localhost:10000";
    final NoopCredentialsProvider credentialsProvider = new NoopCredentialsProvider();
    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .gatewayAddress(gatewayAddress)
            .credentialsProvider(credentialsProvider)
            .build()) {
      final ZeebeClientConfiguration configuration = client.getConfiguration();
      assertThat(configuration.getGatewayAddress()).isEqualTo(gatewayAddress);
      assertThat(configuration.getCredentialsProvider()).isEqualTo(credentialsProvider);
    }
  }

  @Test
  public void shouldCloudBuilderBuildProperClientWithRegionPropertyProvided() {
    // given
    final String region = "asdf-123";
    final Properties properties = new Properties();
    properties.putIfAbsent(CLOUD_REGION, region);
    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .withProperties(properties)
            .build()) {
      // when
      final ZeebeClientConfiguration clientConfiguration = client.getConfiguration();
      // then
      assertThat(clientConfiguration.getCredentialsProvider())
          .isInstanceOf(OAuthCredentialsProvider.class);
      assertThat(clientConfiguration.getGatewayAddress())
          .isEqualTo(String.format("clusterId.%s.zeebe.camunda.io:443", region));
    }
  }

  @Test
  public void shouldCloudBuilderBuildProperClientWithRegionPropertyNotProvided() {
    // given
    final String defaultRegion = "bru-2";
    try (final ZeebeClient client =
        ZeebeClient.newCloudClientBuilder()
            .withClusterId("clusterId")
            .withClientId("clientId")
            .withClientSecret("clientSecret")
            .build()) {
      // when
      final ZeebeClientConfiguration clientConfiguration = client.getConfiguration();
      // then
      assertThat(clientConfiguration.getCredentialsProvider())
          .isInstanceOf(OAuthCredentialsProvider.class);
      assertThat(clientConfiguration.getGatewayAddress())
          .isEqualTo(String.format("clusterId.%s.zeebe.camunda.io:443", defaultRegion));
    }
  }
}
