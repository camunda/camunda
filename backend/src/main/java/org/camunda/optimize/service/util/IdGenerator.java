/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import java.util.UUID;

public class IdGenerator {

  public static String getNextId() {
    UUID randomUUID = UUID.randomUUID();
    return randomUUID.toString();
  }
}
