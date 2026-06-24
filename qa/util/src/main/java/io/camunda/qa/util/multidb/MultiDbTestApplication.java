/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @MultiDbTestApplication} is used to signal that the corresponding {@link io.camunda.zeebe.qa.util.cluster.TestSpringApplication}
 *  is used by the {@link CamundaMultiDBExtension}. The annotated application will be configured and be used to run tests against multiple
 *  databases.
 *  <p/>Per default the lifecycle of the annotated {@link io.camunda.zeebe.qa.util.cluster.TestSpringApplication} is managed by the {@link CamundaMultiDBExtension}. This means the application is started and stopped by the extension.
 *  If this is not wanted, this can be disabled via {@link MultiDbTestApplication#managedLifecycle()}.
 *
 * <pre>{@code
 *   @MultiDbTestApplication(managedLifecycle = false)
 *   private static final TestSimpleCamundaApplication STANDALONE_CAMUNDA =
 *       new TestSimpleCamundaApplication().withBasicAuth().withAuthorizationsEnabled();
 * }</pre>
 *
 *  <p/> This is useful for more complex use cases where the test application lifecycle needs to be managed separately or needs to be further configured before starting.
 *
 * <pre>{@code
 * @MultiDbTest
 * final class MyMultiDbTest {
 *
 *   private static CamundaClient client;
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
 *     topology = c.newTopologyRequest().send().join();
 *
 *     // then
 *     assertThat(topology.getClusterSize()).isEqualTo(1);
 *   }
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MultiDbTestApplication {

  /**
   * @return if true, then {@link CamundaMultiDBExtension} will manage the lifecycle of the test
   *     application. Otherwise, the user is in charge of start and stopping the application.
   */
  boolean managedLifecycle() default true;
}
