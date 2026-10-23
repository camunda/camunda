/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.identity.autoconfigure.IdentityProperties;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.UrlResource;

/**
 * Optimize tells Management Identity which component it is, so that Identity returns only the
 * permissions Optimize's own resource server defines rather than every permission on the audience
 * it asked with. Identity falls back to the old, unscoped behaviour when the component is missing
 * or unrecognised, so a key that fails to reach the SDK is silent: no error, no scoping, and the
 * permission leak this exists to close stays open.
 *
 * <p>Binding into {@link IdentityProperties} rather than reading the property back out of the YAML
 * is what makes this worth having. The value has to land on a component the SDK actually declares,
 * so the test also fails if the {@code identity-sdk} version is rolled back below the release that
 * introduced it.
 */
class CcsmIdentityComponentIdTest {

  /** The key of Optimize's entry under Identity's {@code component-presets}. */
  private static final String PRESET_KEY = "optimize";

  private static final String FILE_NAME = "application-ccsm.yaml";

  /**
   * Picked out of the classpath deliberately: {@code src/it/resources} carries an {@code
   * application-ccsm.yaml} of its own which otherwise shadows this one, and the file that gets
   * packaged is the one worth asserting about.
   */
  private static UrlResource packagedFile() throws IOException {
    final List<URL> found =
        Collections.list(
            CcsmIdentityComponentIdTest.class.getClassLoader().getResources(FILE_NAME));

    return found.stream()
        .filter(url -> url.getPath().contains("/target/classes/"))
        .findFirst()
        .map(UrlResource::new)
        .orElseThrow(() -> new IllegalStateException("not on the classpath: " + found));
  }

  private static IdentityProperties identityPropertiesWith(final Map<String, Object> overrides)
      throws IOException {
    final List<PropertySource<?>> packaged =
        new YamlPropertySourceLoader().load("application-ccsm", packagedFile());

    final StandardEnvironment environment = new StandardEnvironment();
    packaged.forEach(source -> environment.getPropertySources().addLast(source));
    if (!overrides.isEmpty()) {
      environment.getPropertySources().addFirst(new MapPropertySource("overrides", overrides));
    }
    ConfigurationPropertySources.attach(environment);

    return Binder.get(environment).bind("camunda.identity", IdentityProperties.class).get();
  }

  @Test
  void bindsTheComponentIdIdentityKnowsOptimizeBy() throws IOException {
    assertThat(identityPropertiesWith(Map.of()).componentId()).isEqualTo(PRESET_KEY);
  }

  @Test
  void letsADeploymentOverrideIt() throws IOException {
    final IdentityProperties identity =
        identityPropertiesWith(Map.of("CAMUNDA_OPTIMIZE_IDENTITY_COMPONENT_ID", "elsewhere"));

    assertThat(identity.componentId()).isEqualTo("elsewhere");
  }
}
