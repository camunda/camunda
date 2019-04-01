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
export {loadEntity, createEntity, updateEntity} from './entityService';

export {isDurationReport} from './reportService';

// unfortunately, there is no syntax like "export * as formatters from './formatters'"
export const formatters = formattersImport;

export function getRandomId() {
  return Math.random()
    .toString(36)
    .slice(2);
}
