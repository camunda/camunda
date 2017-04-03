import {addLoading, createLoadingActionFunction, createResultActionFunction} from 'utils';

export const SET_VERSION = 'SET_VERSION';
export const LOAD_PROCESSDEFINITIONS = 'LOAD_PROCESSDEFINITIONS';

export const reducer = addLoading((state, {type, key, version}) => {
  const newState = {...state};

  // reset selected versions when (re-)loading processDefinitions
  if (type === LOAD_PROCESSDEFINITIONS) {
    newState.versions = {};
  }

  if (type === SET_VERSION) {
    return {
      ...newState,
      versions: {
        ...newState.versions,
        [key]: version
      }
    };
  }
  return newState;
}, 'processDefinitions');

export function createSetVersionAction(key, version) {
  return {
    type: SET_VERSION,
    key,
    version
  };
}

export const createLoadProcessDefinitionsAction = createLoadingActionFunction('processDefinitions');
export const createLoadProcessDefinitionsResultAction = createResultActionFunction('processDefinitions');
