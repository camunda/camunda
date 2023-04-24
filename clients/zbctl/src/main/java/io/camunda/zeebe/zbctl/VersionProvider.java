/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl;

import io.camunda.zeebe.client.impl.util.VersionUtil;
import picocli.CommandLine.IVersionProvider;

final class VersionProvider implements IVersionProvider {
  private static final String[] VERSION =
      new String[] {"zbctl %s".formatted(VersionUtil.getVersion())};

  @Override
  public String[] getVersion() throws Exception {
    return VERSION;
  }
}
