/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

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
public @interface HistoryMultiDbTest {}
