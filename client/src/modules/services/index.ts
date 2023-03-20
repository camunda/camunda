/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export function getRandomId() {
  return Math.random().toString(36).slice(2);
}

export {TEXT_REPORT_MAX_CHARACTERS, isTextReportTooLong, isTextReportValid} from './reportService';
export {default as getScreenBounds} from './getScreenBounds';
export {default as ignoreFragments} from './ignoreFragments';
export {default as isReactElement} from './isReactElement';
