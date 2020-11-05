/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mockResolvedAsyncFn} from 'modules/testUtils';

export const mocks = {
  onChange: jest.fn(),
  // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
  onFilterChange: mockResolvedAsyncFn(),
  checkIsComplete: jest.fn(),
};
