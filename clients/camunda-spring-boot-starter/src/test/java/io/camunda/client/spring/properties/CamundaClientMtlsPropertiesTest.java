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
package io.camunda.client.spring.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.spring.CamundaClientPropertiesTestConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

public class CamundaClientMtlsPropertiesTest {

  @Nested
  @SpringBootTest(classes = CamundaClientPropertiesTestConfig.class)
  public class Defaults {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldDefaultToNoMtls() {
      assertThat(properties.getMtls().getCertPath()).isNull();
      assertThat(properties.getMtls().getCert()).isNull();
      assertThat(properties.getMtls().getKeyPath()).isNull();
      assertThat(properties.getMtls().getKey()).isNull();
      assertThat(properties.getMtls().getKeyPassphrase()).isNull();
      assertThat(properties.getMtls().getCaPath()).isNull();
      assertThat(properties.getMtls().getCa()).isNull();
      assertThat(properties.getMtls().getKeystorePath()).isNull();
      assertThat(properties.getMtls().getKeystorePassword()).isNull();
      assertThat(properties.getMtls().getKeystoreKeyPassword()).isNull();
      assertThat(properties.getMtls().getTruststorePath()).isNull();
      assertThat(properties.getMtls().getTruststorePassword()).isNull();
    }

    @Test
    void shouldNotLeakSecretsInToString() {
      properties.getMtls().setCert("INLINE CERT");
      properties.getMtls().setKey("INLINE KEY");
      properties.getMtls().setKeyPassphrase("key-secret");
      properties.getMtls().setKeystorePassword("store-secret");
      properties.getMtls().setTruststorePassword("trust-secret");

      assertThat(properties.getMtls().toString())
          .doesNotContain("INLINE CERT")
          .doesNotContain("INLINE KEY")
          .doesNotContain("key-secret")
          .doesNotContain("store-secret")
          .doesNotContain("trust-secret");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {
        "camunda.client.mtls.cert-path=/certs/client.crt",
        "camunda.client.mtls.key-path=/certs/client.key",
        "camunda.client.mtls.key-passphrase=secret",
        "camunda.client.mtls.ca-path=/certs/ca.crt",
      })
  public class PemPaths {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBindPemPaths() {
      assertThat(properties.getMtls().getCertPath()).isEqualTo("/certs/client.crt");
      assertThat(properties.getMtls().getKeyPath()).isEqualTo("/certs/client.key");
      assertThat(properties.getMtls().getKeyPassphrase()).isEqualTo("secret");
      assertThat(properties.getMtls().getCaPath()).isEqualTo("/certs/ca.crt");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {
        "camunda.client.mtls.cert=INLINE CERT",
        "camunda.client.mtls.key=INLINE KEY",
        "camunda.client.mtls.ca=INLINE CA",
      })
  public class PemInline {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBindInlinePem() {
      assertThat(properties.getMtls().getCert()).isEqualTo("INLINE CERT");
      assertThat(properties.getMtls().getKey()).isEqualTo("INLINE KEY");
      assertThat(properties.getMtls().getCa()).isEqualTo("INLINE CA");
    }
  }

  @Nested
  @SpringBootTest(
      classes = CamundaClientPropertiesTestConfig.class,
      properties = {
        "camunda.client.mtls.keystore-path=/certs/client.p12",
        "camunda.client.mtls.keystore-password=storepass",
        "camunda.client.mtls.keystore-key-password=keypass",
        "camunda.client.mtls.truststore-path=/certs/truststore.p12",
        "camunda.client.mtls.truststore-password=trustpass",
      })
  public class Keystore {
    @Autowired CamundaClientProperties properties;

    @Test
    void shouldBindKeystore() {
      assertThat(properties.getMtls().getKeystorePath()).isEqualTo("/certs/client.p12");
      assertThat(properties.getMtls().getKeystorePassword()).isEqualTo("storepass");
      assertThat(properties.getMtls().getKeystoreKeyPassword()).isEqualTo("keypass");
      assertThat(properties.getMtls().getTruststorePath()).isEqualTo("/certs/truststore.p12");
      assertThat(properties.getMtls().getTruststorePassword()).isEqualTo("trustpass");
    }
  }
}
