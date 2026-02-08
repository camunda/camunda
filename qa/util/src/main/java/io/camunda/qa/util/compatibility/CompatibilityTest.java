/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.compatibility;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@code @CompatibilityTest} is used to signal that the annotated test should be run against a
 * specific Camunda version (SNAPSHOT by default). This is useful for compatibility testing across
 * different versions of Camunda.
 *
 * <p>The annotation extends the test with {@link CompatibilityTestExtension}, which:
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @CompatibilityTest
 * final class MyCompatibilityTest {
 *
 *   private static CamundaClient camundaClient;
 *
 *   @UserDefinition
 *   private static final TestUser USER_1 = new TestUser("user1", "password", List.of());
 *
 *   @Test
 *   void shouldWorkWithVersionedCluster() {
 *     // Test using camundaClient
 *   }
 * }
 * }</pre>
 *
 * <p>The version can be specified via:
 *
 * <ul>
 *   <li>System property: {@code -Dcamunda.compatibility.test.version=8.8.1}
 *   <li>Annotation parameter: {@code @CompatibilityTest(version = "8.8.1")}
 * </ul>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("compatibility-test")
@Documented
@ExtendWith(CompatibilityTestExtension.class)
@Inherited
public @interface CompatibilityTest {

  /**
   * The Camunda version to test against. System property {@code camunda.compatibility.test.version}
   * takes precedence if set.
   *
   * @return the version string (e.g., "8.8.1", "8.9.0", "SNAPSHOT")
   */
  String version() default "SNAPSHOT";

  /**
   * @return if true, then a Keycloak container will be started. This is useful for tests that
   *     require OIDC authentication.
   */
  boolean setupKeycloak() default false;

  /**
   * @return if true, then authorizations will be enabled in the Camunda cluster.
   */
  boolean enableAuthorization() default false;

  /**
   * @return if true, then multi-tenancy will be enabled in the Camunda cluster.
   */
  boolean enableMultiTenancy() default false;

  /**
   * Additional environment variables to set on the Camunda container. Each entry should be in the
   * format "KEY=VALUE".
   *
   * <p>Example:
   *
   * <pre>{@code
   * @CompatibilityTest(envVars = {
   *   "CAMUNDA_EXPRESSION_TIMEOUT=PT0.3S",
   *   "CAMUNDA_SOME_OTHER_CONFIG=value"
   * })
   * }</pre>
   *
   * @return array of environment variable assignments in "KEY=VALUE" format
   */
  String[] envVars() default {};
}
