import {addLoading, createLoadingActionFunction, createResultActionFunction, createErrorActionFunction} from 'utils';

export const SET_VERSION = 'SET_VERSION';
export const LOAD_PROCESSDEFINITIONS = 'LOAD_PROCESSDEFINITIONS';

export const LOADING_PROPERTY = 'processDefinitions';

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
}, LOADING_PROPERTY);

export function createSetVersionAction(key, version) {
  return {
    type: SET_VERSION,
    key,
    version
  };
}

export const createLoadProcessDefinitionsAction = createLoadingActionFunction(LOADING_PROPERTY);
export const createLoadProcessDefinitionsResultAction = createResultActionFunction(LOADING_PROPERTY);
export const createLoadProcessDefinitionsErrorAction = createErrorActionFunction(LOADING_PROPERTY);
