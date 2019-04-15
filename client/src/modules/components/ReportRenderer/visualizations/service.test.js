/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {uniteResults} from './service';

it('should unify the keys of all result object by filling empty ones with null', () => {
  expect(
    uniteResults([[{key: 'a', value: 1}, {key: 'b', value: 2}], [{key: 'b', value: 1}]], ['a', 'b'])
  ).toEqual([
    [{key: 'a', value: 1}, {key: 'b', value: 2}],
    [{key: 'a', value: null}, {key: 'b', value: 1}]
  ]);
});
