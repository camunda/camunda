/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export const TEXT_REPORT_MAX_CHARACTERS = 3000;

export function isTextReportValid(textLength: number): boolean {
  return textLength > 0 && !isTextReportTooLong(textLength);
}

export function isTextReportTooLong(textLength: number): boolean {
  return textLength > TEXT_REPORT_MAX_CHARACTERS;
}
