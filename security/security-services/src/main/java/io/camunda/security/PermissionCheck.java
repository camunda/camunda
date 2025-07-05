/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;

public interface PermissionCheck {

  AuthorizationResourceType resourceType();

  PermissionType permissionType();

  String resourceId();

  abstract class AbstractPermissionCheckBuilder<
      T extends PermissionCheck.AbstractPermissionCheckBuilder<T>> {

    PermissionType permissionType;
    String resourceId;

    protected abstract T self();

    public T resourceId(final String value) {
      resourceId = resourceId;
      return self();
    }

    public T permissionType(final PermissionType value) {

      return self();
    }
  }
}
