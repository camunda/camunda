/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

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
 * {@code @MultiDbTest} is used to signal that the annotated test can be run against multiple
 *  databases. The annotation and respective extension is to make things easier, and reduce
 *  unnecessary boilerplate.
 *
 * <p>Respective test is extended with the {@link CamundaMultiDBExtension}, to detect and configure
 * the correct secondary storage.
 *
 * <p> Furthermore, test is part of "multi-db-test" group, which can be executed via maven:
 * `mvn verify -Dgroups="multi-db-test"`
 *
 * <pre>{@code
 * @MultiDbTest
 * final class MyMultiDbTest {
 *
 *   private static CamundaClient client;
 *   private static DatabaseType databaseType;
 *
 *   @Test
 *   void shouldUseInjectedFields() {
 *     // Fields are automatically injected
 *     topology = client.newTopologyRequest().send().join();
 *     assertThat(topology.getClusterSize()).isEqualTo(1);
 *
 *     if (databaseType == DatabaseType.ES) {
 *       // Elasticsearch-specific logic
 *     }
 *   }
 *
 *   @Test
 *   void shouldUseParameterInjection(CamundaClient client, DatabaseType databaseType) {
 *     // Can also use parameter injection instead of fields
 *     topology = client.newTopologyRequest().send().join();
 *     assertThat(topology.getClusterSize()).isEqualTo(1);
 *   }
 * }</pre>
 *
 * For more complex use cases a specific {@link io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication} can be used and annotated with {@link MultiDbTestApplication}
 *
 * <pre>{@code
 * @MultiDbTest
 * final class MyMultiDbTest {
 *
 *   private static CamundaClient client;
 *   private static DatabaseType databaseType;
 *
 *   @MultiDbTestApplication
 *   private static final TestSimpleCamundaApplication STANDALONE_CAMUNDA =
 *       new TestSimpleCamundaApplication().withBasicAuth().withAuthorizationsEnabled();
 *
 *   @Test
 *   void shouldMakeUseOfClient() {
 *     // given
 *     // ... set up
 *
 *     // when
 *     topology = client.newTopologyRequest().send().join();
 *
 *     // then
 *     assertThat(topology.getClusterSize()).isEqualTo(1);
 *     // Can also use databaseType field for conditional logic
 *   }
 * }</pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("multi-db-test")
@Documented
@ExtendWith(CamundaMultiDBExtension.class)
@Inherited
public @interface MultiDbTest {

  /**
   * @return if true, then a Keycoak container will be started. This is useful for tests that
   *     require OIDC authentication.
   */
  boolean setupKeycloak() default false;

  /**
   * Setting this is only for local testing purposes; setting the property {@link
   * CamundaMultiDBExtension#PROP_CAMUNDA_IT_DATABASE_TYPE} will override this. This is NOT a way to
   * limit which DB a test or class should run with; for that, use JUnit's {@code @DisabledIf}
   * matching on the property.
   *
   * @return the database type to run the test with. By default, the test will run with the local
   *     database type as defined in the test configuration.
   */
  DatabaseType value() default DatabaseType.LOCAL;
}
