import {
  isLoadingProcessDefinitions,
  haveNoProcessDefinitions,
  getDefinitionId as getDefinitionIdFromProcessDefinition
} from './processDefinition';

export function areControlsLoadingSomething({processDefinition}) {
  return isLoadingProcessDefinitions(processDefinition);
}

export function isDataEmpty({processDefinition}) {
  return haveNoProcessDefinitions(processDefinition);
}

export function getDefinitionId({processDefinition}) {
  return getDefinitionIdFromProcessDefinition(processDefinition);
}
