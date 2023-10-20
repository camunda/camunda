/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getCurrentCopyrightNoticeText} from './getCurrentCopyrightNoticeText';

describe('getCurrentCopyrightNoticeText()', () => {
  it('should return the copyright notice with the correct year', () => {
    vi.useFakeTimers();
    const mockYear = 1999;
    vi.setSystemTime(new Date(mockYear, 0));

    expect(getCurrentCopyrightNoticeText()).toBe(
      `© Camunda Services GmbH ${mockYear}. All rights reserved. | 1.2.3`,
    );
    vi.useRealTimers();
  });
});
