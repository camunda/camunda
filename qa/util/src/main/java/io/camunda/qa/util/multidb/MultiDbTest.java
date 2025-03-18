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
 *   private CamundaClient client;
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
 *
 * For more complex use cases a specific {@link io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication} can be used and annotated with {@link MultiDbTestApplication}
 *
 * <pre>{@code
 * @MultiDbTest
 * final class MyMultiDbTest {
 *
 *   private CamundaClient client;
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
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("multi-db-test")
@Documented
@ExtendWith(CamundaMultiDBExtension.class)
@Inherited
public @interface MultiDbTest {}
