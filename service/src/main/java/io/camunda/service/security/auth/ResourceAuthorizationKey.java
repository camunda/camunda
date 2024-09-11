/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.security.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark fields that are subject to resource authorization checks.
 *
 * <p>Fields annotated with {@code @ResourceAuthorizationKey} indicate that they are involved in
 * determining whether a user has the appropriate access rights for a specific resource type. These
 * fields are used during the authorization filtering process, where the system checks if the
 * authenticated user has permission to access the associated resource.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public record ProcessInstanceEntity(
 *         Long key,
 *         String processName,
 *         Integer processVersion,
 *         @ResourceAuthorizationKey(resourceType = "process-definition") String bpmnProcessId) {}
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ResourceAuthorizationKey {

  /**
   * Defines the type of resource that this field represents for authorization purposes. This is
   * used to match the resource type with the user's authorizations.
   */
  String forResourceType();
}
