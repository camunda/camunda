/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {isTextReportTooLong, isTextReportValid} from './reportService';

describe('isTextReportValid', () => {
  it('should return true if report is valid', () => {
    expect(isTextReportValid(100)).toBe(true);
  });

  it('should return false if report is not valid', () => {
    expect(isTextReportValid(0)).toBe(false);
    expect(isTextReportValid(3001)).toBe(false);
  });
});

describe('isTextReportTooLong', () => {
  it('should return true if report is too long', () => {
    expect(isTextReportTooLong(3001)).toBe(true);
  });

  it('should return false if report is not too long', () => {
    expect(isTextReportTooLong(100)).toBe(false);
  });
});
