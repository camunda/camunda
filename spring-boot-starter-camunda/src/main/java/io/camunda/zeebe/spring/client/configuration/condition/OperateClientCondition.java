package io.camunda.zeebe.spring.client.configuration.condition;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public class OperateClientCondition extends AnyNestedCondition {
  public OperateClientCondition() {
    super(ConfigurationPhase.PARSE_CONFIGURATION);
  }

  @ConditionalOnProperty(name = "camunda.operate.client.client-id")
  static class ClientIdCondition {}

  @ConditionalOnProperty(name = "camunda.operate.client.username")
  static class UsernameCondition {}

  @ConditionalOnProperty(name = "camunda.operate.client.auth-url")
  static class AuthUrlCondition {}

  @ConditionalOnProperty(name = "camunda.operate.client.base-url")
  static class BaseUrlCondition {}

  @ConditionalOnProperty(name = "camunda.operate.client.keycloak-url")
  static class KeycloakUrlCondition {}

  @ConditionalOnProperty(name = "camunda.operate.client.keycloak-token-url")
  static class KeycloakTokenUrlCondition {}

  @ConditionalOnProperty(name = "camunda.operate.client.url")
  static class UrlCondition {}

  @ConditionalOnProperty(name = "camunda.operate.client.enabled")
  static class EnableCondition {}
}
