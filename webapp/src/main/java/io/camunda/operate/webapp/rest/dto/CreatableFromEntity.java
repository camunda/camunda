/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.entities.OperateEntity;

public interface CreatableFromEntity<T extends CreatableFromEntity<T, E>, E extends OperateEntity> {

  T fillFrom(E entity);

}
