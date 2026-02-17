/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.index;

import static io.camunda.webapps.schema.descriptors.ComponentNames.CAMUNDA;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;

public class GlobalListenerIndex extends AbstractIndexDescriptor implements Prio5Backup {

  public static final String INDEX_NAME = "global-listener";
  public static final String INDEX_VERSION = "8.9.0";

  public static final String ID = "id";
  public static final String LISTENER_ID = "listenerId";
  public static final String TYPE = "type";
  public static final String RETRIES = "retries";
  public static final String EVENT_TYPES = "eventTypes";
  public static final String AFTER_NON_GLOBAL = "afterNonGlobal";
  public static final String PRIORITY = "priority";
  public static final String SOURCE = "source";
  public static final String LISTENER_TYPE = "listenerType";

  public GlobalListenerIndex(final String indexPrefix, final boolean isElasticsearch) {
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

  @Override
  public String getComponentName() {
    return CAMUNDA.toString();
  }
}
