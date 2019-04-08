/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import javax.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Based on NameBinding feature of JAX-RS implementation, this annotation
 * is mapping specific methods of JAX-RS resources to AuthenticationFilter which is responsible
 * for security constraints validation.
 *
 * I.e. this annotation should be used to demarcate methods that require valid authentication token in
 * request header in order to be invoked.
 */
@NameBinding
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Secured { }