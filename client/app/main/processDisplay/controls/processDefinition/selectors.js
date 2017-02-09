import {isLoading} from 'utils';

export function getDefinitionId({selected}) {
  return selected;
}

export function isLoadingProcessDefinitions({availableProcessDefinitions}) {
  return isLoading(availableProcessDefinitions);
}

export function haveNoProcessDefinitions({availableProcessDefinitions: {data}}) {
  return !data || data.length === 0;
}
