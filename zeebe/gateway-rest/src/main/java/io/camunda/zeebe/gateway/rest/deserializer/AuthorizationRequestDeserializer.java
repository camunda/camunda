/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AuthorizationIdBasedRequest;
import io.camunda.gateway.protocol.model.AuthorizationPropertyBasedRequest;
import io.camunda.gateway.protocol.model.AuthorizationRequest;
import java.util.List;
import java.util.Set;

public class AuthorizationRequestDeserializer
    extends AbstractRequestDeserializer<AuthorizationRequest> {

  private static final String RESOURCE_ID_FIELD = "resourceId";
  private static final String RESOURCE_PROPERTY_NAME_FIELD = "resourcePropertyName";
  private static final List<String> SUPPORTED_FIELDS =
      List.of(RESOURCE_ID_FIELD, RESOURCE_PROPERTY_NAME_FIELD);

  @Override
  protected List<String> getSupportedFields() {
    return SUPPORTED_FIELDS;
  }

  @Override
  protected Class<? extends AuthorizationRequest> getResultType(final Set<String> presentFields) {
    if (presentFields.contains(RESOURCE_PROPERTY_NAME_FIELD)) {
      return AuthorizationPropertyBasedRequest.class;
    }
    return AuthorizationIdBasedRequest.class;
  }
}
