/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.versioned;

import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@code @VersionedTest} is used to signal that the annotated test should run against a specific
 * Camunda version using Docker containers with configurable database backend.
 *
 * <p>The annotation extends the test with {@link VersionedTestExtension}, which:
 * <ul>
 *   <li>Starts a database container (Elasticsearch by default)
 *   <li>Starts a Camunda container with the specified version
 *   <li>Configures authentication and authorization if needed
 *   <li>Creates entities defined via annotations (@UserDefinition, @GroupDefinition, etc.)
 *   <li>Injects authenticated clients
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * @VersionedTest
 * final class MyVersionedTest {
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
 * <ul>
 *   <li>System property: {@code -Dcamunda.test.version=8.8.1}
 *   <li>Annotation parameter: {@code @VersionedTest(version = "8.8.1")}
 * </ul>
 *
 * <p>The database can be specified via:
 * <ul>
 *   <li>System property: {@code -Dtest.integration.camunda.database.type=ES}
 *   <li>Annotation parameter: {@code @VersionedTest(database = DatabaseType.ES)}
 * </ul>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("versioned-test")
@Documented
@ExtendWith(VersionedTestExtension.class)
@Inherited
public @interface VersionedTest {

  /**
   * The Camunda version to test against. System property {@code camunda.test.version} takes
   * precedence if set.
   *
   * @return the version string (e.g., "8.8.1", "8.9.0", "SNAPSHOT")
   */
  String version() default "SNAPSHOT";

  /**
   * @return if true, then a Keycloak container will be started. This is useful for tests that
   *     require OIDC authentication.
   */
  boolean setupKeycloak() default false;

  boolean enableAuthorization() default false;
}
