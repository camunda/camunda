/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import io.camunda.client.CamundaClient;
import io.camunda.security.configuration.InitializationConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be passed along with {@link BrokerITInvocationProvider}'s {@link
 * org.junit.jupiter.api.TestTemplate}. When applied, this indicates that the {@link CamundaClient}
 * should be created with the provided user's credentials.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Authenticated {

  /** The username of the user to be used for authentication. */
  String value() default InitializationConfiguration.DEFAULT_USER_USERNAME;
}
