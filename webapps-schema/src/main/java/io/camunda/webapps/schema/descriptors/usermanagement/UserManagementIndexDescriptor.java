/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.usermanagement;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import java.util.Optional;

public abstract class UserManagementIndexDescriptor extends AbstractIndexDescriptor {

  public UserManagementIndexDescriptor(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getComponentName() {
    return ComponentNames.CAMUNDA.toString();
  }

  @Override
  public Optional<String> getTenantIdField() {
    return Optional.empty();
  }
}
