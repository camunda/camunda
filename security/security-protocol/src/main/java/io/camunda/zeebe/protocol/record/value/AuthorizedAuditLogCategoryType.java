/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum AuthorizedAuditLogCategoryType {
  ADMIN,
  USER_TASKS;

  public static List<String> getAuthorizedCategories() {
    return Arrays.stream(values()).map(Enum::name).collect(Collectors.toList());
  }
}
