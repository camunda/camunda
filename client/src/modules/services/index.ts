/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export function getRandomId() {
  return Math.random().toString(36).slice(2);
}

export {loadReports, loadEntities} from './entityService';

export * as formatters from './formatters';
export {loadProcessDefinitionXml, loadDecisionDefinitionXml} from './dataLoaders';
export {numberParser} from './NumberParser';

export {
  TEXT_REPORT_MAX_CHARACTERS,
  isTextTileTooLong,
  isTextTileValid,
  loadRawData,
} from './reportService';

export {addSources, getCollection} from './collectionService';

export {default as getScreenBounds} from './getScreenBounds';
export {default as ignoreFragments} from './ignoreFragments';
export {default as isReactElement} from './isReactElement';
