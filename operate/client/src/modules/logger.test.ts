/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {logger} from './logger';

const ORIGINAL_NODE_ENV = process.env.NODE_ENV;

function setNodeEnv(newValue: typeof process.env.NODE_ENV) {
  Object.defineProperty(process.env, 'NODE_ENV', {value: newValue});
}

const mockConsoleError = jest
  .spyOn(console, 'error')
  .mockImplementation(() => {});

describe('logger', () => {
  beforeEach(() => {
    setNodeEnv(ORIGINAL_NODE_ENV);
    mockConsoleError.mockReset();
  });

  afterAll(() => {
    setNodeEnv(ORIGINAL_NODE_ENV);
    mockConsoleError.mockRestore();
  });

  it('should log an error', () => {
    const mockError = 'a random error';

    setNodeEnv('production');
    logger.error(mockError);

    expect(mockConsoleError).toHaveBeenNthCalledWith(1, mockError);

    setNodeEnv('development');
    logger.error(mockError);

    expect(mockConsoleError).toHaveBeenCalledTimes(2);
    expect(mockConsoleError).toHaveBeenNthCalledWith(2, mockError);
  });

  it('should not log an error', () => {
    const mockError = 'a random error';

    setNodeEnv('test');
    logger.error(mockError);

    expect(mockConsoleError).not.toHaveBeenCalled();
  });
});
