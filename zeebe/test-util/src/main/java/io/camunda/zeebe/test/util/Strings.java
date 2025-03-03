/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util;

import java.util.UUID;

public final class Strings {

  /**
   * @return a prefixed random UUID which can be used as a valid ID for a BPMN element
   */
  public static String newRandomValidBpmnId() {
    return "id-" + UUID.randomUUID().toString();
  }

  public static String newRandomValidUsername() {
    return "user" + UUID.randomUUID().toString().replace("-", "");
  }

  public static String newRandomValidIdentityId() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
