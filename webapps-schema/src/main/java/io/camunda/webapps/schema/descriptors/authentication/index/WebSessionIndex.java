/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.authentication.index;

import io.camunda.webapps.schema.descriptors.authentication.AuthenticationIndexDescriptor;

public class WebSessionIndex extends AuthenticationIndexDescriptor {

  public static final String INDEX_NAME = "web-session";
  public static final String INDEX_VERSION = "8.7.0";

  public WebSessionIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }
}
