/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@code @MultiDbTest} is used to signal that the annotated test can be run against multiple
 * databases.
 *
 * <p>Respective test is extended with the {@link CamundaMultiDBExtension}, to detect and configure
 * the correct secondary storage.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("multi-db-test")
@ExtendWith(CamundaMultiDBExtension.class)
@Inherited
public @interface MultiDbTest {}
