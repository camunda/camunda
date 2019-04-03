/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {filterEntitiesBySearch} from './service';

it('should filter entities by search correctly', () => {
  expect(filterEntitiesBySearch([{name: 'test name'}, {name: 'Another Name'}], 'Test')).toEqual([
    {name: 'test name'}
  ]);
});
