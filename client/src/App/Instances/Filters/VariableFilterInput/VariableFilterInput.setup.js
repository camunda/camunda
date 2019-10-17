/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mockResolvedAsyncFn} from 'modules/testUtils';

export const mockDefaultProps = {
  variable: {name: '', value: ''},
  onFilterChange: mockResolvedAsyncFn(),
  onChange: jest.fn(),
  checkIsNameComplete: jest.fn(),
  checkIsValueComplete: jest.fn(),
  checkIsValueValid: jest.fn()
};
