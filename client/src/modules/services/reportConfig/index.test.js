/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import config from './index';

jest.mock('./reportConfig', () => () => ({update: () => ({})}));

describe('process update', () => {
  it('should reset hidden nodes configuration when switching to user task view', () => {
    const changes = config.process.update(
      'view',
      {property: 'duration', entity: 'userTask'},
      {report: {data: {view: {entity: 'flowNode', property: 'duration'}}}}
    );

    expect(changes.configuration.hiddenNodes).toEqual({$set: []});
  });
  it('should not reset hidden nodes configuration when switching between different flow node views', () => {
    const changes = config.process.update(
      'view',
      {property: 'duration', entity: 'flowNode'},
      {report: {data: {view: {entity: 'flowNode', property: 'frequency'}}}}
    );

    expect(changes.configuration.hiddenNodes).not.toBeDefined();
  });
});
