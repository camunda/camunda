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
 * Extension of the {@link MultiDbTest} annotation, to mark a test as history related test, which
 * should be tested against multiple databases.
 *
 * <p>Annotation communicates to {@link CamundaMultiDBExtension} such that configures history clean
 * up.
 *
 * <p/> See for more details {@link MultiDbTest}
 *
 * <p> Test is part of "history-multi-db-test" group, which can be executed via maven:
 * `mvn verify -Dgroups="history-multi-db-test"`
 *
 * <pre>{@code
 * @HistoryMultiDbTest
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
 *     // client complete task - PI completion
 *
 *     // then
 *     // assert data is cleaned up
 *   }
 * }</pre>
 *
 * @see MultiDbTest
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("history-multi-db-test")
@Documented
@ExtendWith(CamundaMultiDBExtension.class)
@Inherited
public @interface HistoryMultiDbTest {}
