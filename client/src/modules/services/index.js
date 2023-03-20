/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import * as formattersImport from './formatters';

export {
  getFlowNodeNames,
  loadProcessDefinitionXml,
  loadDecisionDefinitionXml,
  checkDeleteConflict,
  loadVariables,
  loadInputVariables,
  loadOutputVariables,
} from './dataLoaders';
export {numberParser} from './NumberParser';
export {incompatibleFilters} from './incompatibleFilters';
export {default as reportConfig, createReportUpdate, getDefaultSorting} from './reportConfig';
export {getDiagramElementsBetween} from './diagramServices';
export {default as getTooltipText} from './getTooltipText';
export {default as getScreenBounds} from './getScreenBounds';
export {
  loadEntity,
  loadReports,
  createEntity,
  updateEntity,
  deleteEntity,
  copyReport,
} from './entityService';

export {
  evaluateReport,
  isDurationReport,
  loadRawData,
  getReportResult,
  processResult,
  isAlertCompatibleReport,
  isCategoricalBar,
  isCategorical,
  TEXT_REPORT_MAX_CHARACTERS,
  isTextReportTooLong,
  isTextReportValid,
} from './reportService';

// unfortunately, there is no syntax like "export * as formatters from './formatters'"
export const formatters = formattersImport;

export function getRandomId() {
  return Math.random().toString(36).slice(2);
}

export function capitalize(string) {
  return string.charAt(0).toUpperCase() + string.slice(1);
}

export function getCollection(path) {
  const collectionMatch = /\/collection\/([^/]+)/g.exec(path);
  return collectionMatch && collectionMatch[1];
}

export {loadAlerts, addAlert, removeAlert, editAlert} from './alertService';
export {default as ignoreFragments} from './ignoreFragments';
export {default as isReactElement} from './isReactElement';
