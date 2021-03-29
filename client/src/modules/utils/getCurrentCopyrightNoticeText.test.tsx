/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getCurrentCopyrightNoticeText} from './getCurrentCopyrightNoticeText';

const getFullYearMock = jest.spyOn(Date.prototype, 'getFullYear');

describe('getCurrentCopyrightNoticeText()', () => {
  afterAll(() => {
    getFullYearMock.mockRestore();
  });

  it('should return the copyright notice with the correct year', () => {
    const mockYear = 1999;

    getFullYearMock.mockReturnValueOnce(mockYear);

    expect(getCurrentCopyrightNoticeText()).toBe(
      `© Camunda Services GmbH ${mockYear}. All rights reserved. | 1.2.3`,
    );
  });
});
