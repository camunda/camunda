/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mockResolvedAsyncFn} from 'modules/testUtils';

export const mockDefaultProps = {
  variable: {name: '', value: ''},
  // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
  onFilterChange: mockResolvedAsyncFn(),
  onChange: jest.fn(),
  checkIsNameComplete: jest.fn(),
  checkIsValueComplete: jest.fn(),
  checkIsValueValid: jest.fn(),
  errorMessage: 'Variable has to be filled and Value has to be JSON',
};
