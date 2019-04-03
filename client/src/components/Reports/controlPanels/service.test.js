/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isDurationHeatmap, isProcessInstanceDuration} from './service';

it('Should correctly check for duration heatmap', () => {
  expect(
    isDurationHeatmap({
      view: {entity: 'flowNode', property: 'duration'},
      visualization: 'heat',
      processDefinitionKey: 'test',
      processDefinitionVersion: 'test'
    })
  ).toBeTruthy();
});

it('should correclty check for process instance duration reports', () => {
  expect(
    isProcessInstanceDuration({view: {entity: 'processInstance', property: 'duration'}})
  ).toBeTruthy();
});
