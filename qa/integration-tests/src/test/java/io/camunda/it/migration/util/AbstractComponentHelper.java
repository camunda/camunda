/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration.util;

import io.camunda.search.connect.configuration.DatabaseType;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.Network;

public abstract class AbstractComponentHelper<SELF extends AbstractComponentHelper>
    implements AutoCloseable {

  protected ZeebeComponentHelper zeebeComponentHelper;
  protected Network network;
  protected String indexPrefix;

  public AbstractComponentHelper(
      final ZeebeComponentHelper zeebeComponentHelper,
      final Network network,
      final String indexPrefix) {
    this.zeebeComponentHelper = zeebeComponentHelper;
    this.network = network;
    this.indexPrefix = indexPrefix;
  }

  public AbstractComponentHelper(final Network network, final String indexPrefix) {
    this.network = network;
    this.indexPrefix = indexPrefix;
  }

  public SELF initial(final DatabaseType type) {
    return initial(type, new HashMap<>());
  }

  public abstract SELF initial(DatabaseType type, Map<String, String> envOverrides);

  public SELF update(final DatabaseType type) {
    return update(type, new HashMap<>());
  }

  public abstract SELF update(DatabaseType type, Map<String, String> envOverrides);
}
