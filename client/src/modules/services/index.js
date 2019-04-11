/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as formattersImport from './formatters';

export {
  getOptimizeVersion,
  getFlowNodeNames,
  loadDefinitions,
  loadProcessDefinitionXml,
  loadDecisionDefinitionXml,
  checkDeleteConflict
} from './dataLoaders';
export {numberParser} from './NumberParser';
export {isDurationValue} from './isDurationValue';
export {incompatibleFilters} from './incompatibleFilters';
export {default as reportConfig} from './reportConfig';
export {getDiagramElementsBetween} from './diagramServices';
export {default as getDataKeys} from './getDataKeys';
export {flatten} from './tableServices';
export {default as getTooltipText} from './getTooltipText';
export {extractDefinitionName} from './definitionService';
export {toggleEntityCollection, getEntitiesCollections} from './collectionService';
export {loadEntity, loadEntities, createEntity, updateEntity, deleteEntity} from './entityService';

export {evaluateReport, isDurationReport} from './reportService';

// unfortunately, there is no syntax like "export * as formatters from './formatters'"
export const formatters = formattersImport;

export function getRandomId() {
  return Math.random()
    .toString(36)
    .slice(2);
}
