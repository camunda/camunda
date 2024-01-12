/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.operate.json;

import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import org.jeasy.random.EasyRandom;

public class OperateEntityFactory {

  private EasyRandom generator = new EasyRandom();

  public ProcessInstanceForListViewEntity buildProcessInstanceForListViewEntity() {
    ProcessInstanceForListViewEntity entity =
        generator.nextObject(ProcessInstanceForListViewEntity.class);
    entity.getJoinRelation().setParent(123L); // for some reason this doesn't get generated

    return entity;
  }
}
