/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

public enum CollectionRole {
  // note: the order matters here, the order of roles corresponds to more might
  VIEWER,
  EDITOR,
  MANAGER,
  ;
}
