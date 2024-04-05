/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {logger} from './logger';

const ORIGINAL_NODE_ENV = import.meta.env.MODE;

function setNodeEnv(newValue: string) {
  Object.defineProperty(import.meta.env, 'MODE', {
    value: newValue,
    configurable: true,
    enumerable: true,
    writable: true,
  });
}

describe('logger', () => {
  beforeEach(() => {
    setNodeEnv(ORIGINAL_NODE_ENV);
  });

  afterAll(() => {
    setNodeEnv(ORIGINAL_NODE_ENV);
  });

  it('should log an error', () => {
    const mockConsoleError = vi
      .spyOn(console, 'error')
      .mockImplementation(() => {});
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
    const mockConsoleError = vi
      .spyOn(console, 'error')
      .mockImplementation(() => {});
    const mockError = 'a random error';

    setNodeEnv('test');
    logger.error(mockError);

    expect(mockConsoleError).not.toHaveBeenCalled();
  });
});
