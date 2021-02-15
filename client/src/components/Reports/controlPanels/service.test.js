/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isDurationHeatmap, isProcessInstanceDuration} from './service';

it('should correctly check for duration heatmap', () => {
  expect(
    isDurationHeatmap({
      view: {entity: 'flowNode', properties: ['duration']},
      visualization: 'heat',
      processDefinitionKey: 'test',
      processDefinitionVersions: ['test'],
    })
  ).toBeTruthy();
});

it('should work for user task reports', () => {
  expect(
    isDurationHeatmap({
      view: {entity: 'userTask', properties: ['duration']},
      visualization: 'heat',
      processDefinitionKey: 'test',
      processDefinitionVersions: ['test'],
    })
  ).toBeTruthy();

  expect(
    isDurationHeatmap({
      view: {entity: 'userTask', properties: ['frequency']},
      visualization: 'heat',
      processDefinitionKey: 'test',
      processDefinitionVersions: ['test'],
    })
  ).toBeFalsy();
});

it('should correclty check for process instance duration reports', () => {
  expect(
    isProcessInstanceDuration({view: {entity: 'processInstance', properties: ['duration']}})
  ).toBeTruthy();
});
