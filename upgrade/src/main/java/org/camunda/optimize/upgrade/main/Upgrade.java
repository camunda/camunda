/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main;

import java.io.IOException;

public interface Upgrade {

  String getInitialVersion();
  String getTargetVersion();

  void performUpgrade();

  void checkTargetRequiredVersions() throws IOException;
}
