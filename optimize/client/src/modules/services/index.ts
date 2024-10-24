/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export function getRandomId() {
  return Math.random().toString(36).slice(2);
}

export {
  loadReports,
  loadEntities,
  copyEntity,
  deleteEntity,
  createEntity,
  getEntityIcon,
} from './entityService';
export {UNAUTHORIZED_TENANT_ID} from './tenantService';
export * as formatters from './formatters';
export {loadProcessDefinitionXml, loadVariables} from './dataLoaders';
export {numberParser} from './NumberParser';
export {
  TEXT_REPORT_MAX_CHARACTERS,
  isTextTileTooLong,
  isTextTileValid,
  loadRawData,
  evaluateReport,
} from './reportService';
export {addSources, getCollection} from './collectionService';
export {default as getScreenBounds} from './getScreenBounds';
export {default as ignoreFragments} from './ignoreFragments';
export {default as isReactElement} from './isReactElement';
export {incompatibleFilters} from './incompatibleFilters';
export {loadDefinitions} from './loadDefinitions';

export type {Definition} from './loadDefinitions';
export type {ReportEvaluationPayload} from './reportService';
