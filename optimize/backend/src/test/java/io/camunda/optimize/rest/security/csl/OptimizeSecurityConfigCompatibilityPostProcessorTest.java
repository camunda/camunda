/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.impl.NoOpLog;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class OptimizeSecurityConfigCompatibilityPostProcessorTest {

  private static final String OIDC = "camunda.security.authentication.oidc.";

  private final OptimizeSecurityConfigCompatibilityPostProcessor processor =
      new OptimizeSecurityConfigCompatibilityPostProcessor(destination -> new NoOpLog());

  private StandardEnvironment environmentWith(final Map<String, Object> legacy) {
    final StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test-legacy", legacy));
    return env;
  }

  @Test
  void bridgesAuth0CloudConfig() {
    final Map<String, Object> legacy = new HashMap<>();
    legacy.put("optimize.security.csl.enabled", "true");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTSECRET", "cloud-secret");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_DOMAIN", "weblogin.example.com");
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_ORGANIZATION", "org-42");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_CLUSTERID", "cluster-7");
    legacy.put("CAMUNDA_OPTIMIZE_CLIENT_AUDIENCE", "optimize");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty("camunda.security.authentication.method")).isEqualTo("oidc");
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("cloud-client");
    assertThat(env.getProperty(OIDC + "client-secret")).isEqualTo("cloud-secret");
    assertThat(env.getProperty(OIDC + "issuer-uri")).isEqualTo("https://weblogin.example.com/");
    assertThat(env.getProperty(OIDC + "audiences")).isEqualTo("optimize");
    assertThat(env.getProperty(OIDC + "organization-id")).isEqualTo("org-42");
    assertThat(env.getProperty(OIDC + "redirect-uri")).isEqualTo("{baseUrl}/sso-callback");
    assertThat(env.getProperty("camunda.security.saas.organization-id")).isEqualTo("org-42");
    assertThat(env.getProperty("camunda.security.saas.cluster-id")).isEqualTo("cluster-7");
  }

  @Test
  void bridgesCcsmConfigWithoutCloudArtifacts() {
    final Map<String, Object> legacy = new HashMap<>();
    legacy.put("optimize.security.csl.enabled", "true");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_ISSUER_URL", "http://localhost:18080/realm");
    legacy.put("CAMUNDA_OPTIMIZE_IDENTITY_CLIENTID", "optimize");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty(OIDC + "issuer-uri")).isEqualTo("http://localhost:18080/realm");
    assertThat(env.getProperty(OIDC + "client-id")).isEqualTo("optimize");
    assertThat(env.getProperty(OIDC + "redirect-uri"))
        .isEqualTo("{baseUrl}/api/authentication/callback");
    assertThat(env.getProperty("camunda.security.saas.organization-id")).isNull();
    assertThat(env.getProperty("camunda.security.saas.cluster-id")).isNull();
  }

  @Test
  void doesNothingWhenCslDisabled() {
    final Map<String, Object> legacy = new HashMap<>();
    legacy.put("CAMUNDA_OPTIMIZE_AUTH0_CLIENTID", "cloud-client");

    final StandardEnvironment env = environmentWith(legacy);
    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty("camunda.security.authentication.method")).isNull();
    assertThat(env.getProperty(OIDC + "client-id")).isNull();
  }
}
