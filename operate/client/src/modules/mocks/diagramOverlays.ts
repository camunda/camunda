/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const createStartEventOverlay = (container: HTMLElement, type: string) => {
  return {
    payload: {data: {foo: 'example start event data'}},
    container,
    flowNodeId: 'startEvent_1',
    type,
  };
};

const createTaskOverlay = (container: HTMLElement, type: string) => {
  return {
    payload: {data: {foo: 'example task data'}},
    container,
    flowNodeId: 'task_1',
    type,
  };
};

export {createStartEventOverlay, createTaskOverlay};
