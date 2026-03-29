/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Extension of the {@link MultiDbTest} annotation, to mark a test as history related test, which
 * should be tested against multiple databases.
 *
 * <p>Annotation communicates to {@link CamundaMultiDBExtension} such that configures history clean
 * up.
 *
 * @see MultiDbTest
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@MultiDbTest
public @interface HistoryMultiDbTest {

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
