/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.oauth2;

import static io.camunda.operate.webapp.security.oauth2.CCSaaSJwtAuthenticationTokenValidator.CLUSTER_ID_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      CCSaaSJwtAuthenticationTokenValidator.class,
      OperateProperties.class,
    },
    properties = {
      OperateProperties.PREFIX + ".client.audience = test.operate.camunda.com",
      OperateProperties.PREFIX + ".cloud.clusterId = my-cluster",
    })
public class CCaaSJwtAuthenticationTokenValidatorIT {

  @Autowired private CCSaaSJwtAuthenticationTokenValidator jwtAuthenticationTokenValidator;

  @Test
  public void shouldNotValidForWrongAudience() {
    final JwtAuthenticationToken token =
        createJwtAuthenticationTokenWith("my.operate.camunda.com", "my-cluster");
    assertThat(jwtAuthenticationTokenValidator.isValid(token)).isFalse();
  }

  @Test
  public void shouldNotValidForWrongScope() {
    final JwtAuthenticationToken token =
        createJwtAuthenticationTokenWith("my.operate.camunda.com", "your-cluster");
    assertThat(jwtAuthenticationTokenValidator.isValid(token)).isFalse();
  }

  @Test
  public void shouldValid() {
    final JwtAuthenticationToken token =
        createJwtAuthenticationTokenWith("test.operate.camunda.com", "my-cluster");
    assertThat(jwtAuthenticationTokenValidator.isValid(token)).isTrue();
  }

  @Test
  public void shouldInvalidDueToScopeIsNotStringOrList() {
    final JwtAuthenticationToken token =
        createJwtAuthenticationTokenWith(
            "test.operate.camunda.com", Map.of("my-cluster", "second-cluster"));
    assertThat(jwtAuthenticationTokenValidator.isValid(token)).isFalse();
  }

  @Test
  public void shouldInvalidDueToScopeIsNull() {
    final JwtAuthenticationToken token =
        createJwtAuthenticationTokenWith("test.operate.camunda.com", null);
    assertThat(jwtAuthenticationTokenValidator.isValid(token)).isFalse();
  }

  protected JwtAuthenticationToken createJwtAuthenticationTokenWith(
      final String audience, final Object scope) {
    return new JwtAuthenticationToken(
        Jwt.withTokenValue("token")
            .audience(List.of(audience))
            .header("alg", "HS256")
            .claim(CLUSTER_ID_CLAIM, scope)
            .build());
  }
}
