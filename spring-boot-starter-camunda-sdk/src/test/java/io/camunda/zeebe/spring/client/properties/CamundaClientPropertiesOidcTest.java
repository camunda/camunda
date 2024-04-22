package io.camunda.zeebe.spring.client.properties;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = CamundaClientPropertiesTestConfig.class,
    properties = "camunda.client.mode=oidc")
public class CamundaClientPropertiesOidcTest {
  @Autowired CamundaClientProperties properties;

  @Test
  void shouldLoadDefaultsOidc() {
    assertThat(properties.getMode()).isEqualTo(ClientMode.oidc);
    assertThat(properties.getZeebe().getGatewayUrl().toString())
        .isEqualTo("http://localhost:26500");
    assertThat(properties.getZeebe().getBaseUrl().toString()).isEqualTo("http://localhost:8086");
    assertThat(properties.getZeebe().isPreferRestOverGrpc()).isEqualTo(false);
    assertThat(properties.getZeebe().getEnabled()).isEqualTo(true);
    assertThat(properties.getOperate().getBaseUrl().toString()).isEqualTo("http://localhost:8081");
    assertThat(properties.getOperate().getEnabled()).isEqualTo(true);
    assertThat(properties.getTasklist().getBaseUrl().toString()).isEqualTo("http://localhost:8082");
    assertThat(properties.getTasklist().getEnabled()).isEqualTo(true);
    assertThat(properties.getOptimize().getEnabled()).isEqualTo(true);
    assertThat(properties.getIdentity().getEnabled()).isEqualTo(true);
    assertThat(properties.getIdentity().getBaseUrl().toString()).isEqualTo("http://localhost:8084");
    assertThat(properties.getZeebe().getAudience()).isEqualTo("zeebe-api");
    assertThat(properties.getOperate().getAudience()).isEqualTo("operate-api");
    assertThat(properties.getTasklist().getAudience()).isEqualTo("tasklist-api");
    assertThat(properties.getIdentity().getAudience()).isEqualTo("identity-api");
  }
}
