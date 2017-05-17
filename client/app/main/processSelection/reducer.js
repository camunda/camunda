import {addLoading, createLoadingActionFunction, createResultActionFunction, createErrorActionFunction} from 'utils';

export const SET_VERSION = 'SET_VERSION';
export const LOAD_PROCESSDEFINITIONS = 'LOAD_PROCESSDEFINITIONS';

export const LOADING_PROPERTY = 'processDefinitions';

export const reducer = addLoading((state, {type, key, version, xml}) => {
  if (state[LOADING_PROPERTY]) {
    const {[LOADING_PROPERTY]: {data: definitions, ...meta}} = state;

    if (type === SET_VERSION) {
      return {
        ...state,
        [LOADING_PROPERTY]: {
          ...meta,
          data: definitions.map(entry => {
            if (entry.current.key === key) {
              let current = entry.versions.filter(({version: otherVersion}) => otherVersion === version)[0];

              if (xml) {
                current = {
                  ...current,
                  bpmn20Xml: xml
                };
              }

              return {
                current,
                versions: entry.versions
              };
            }

            return entry;
          })
        }
      };
    }
  }

  return state;
}, LOADING_PROPERTY);

export function createSetVersionAction(key, version, xml) {
  return {
    type: SET_VERSION,
    key,
    version,
    xml
  };
}

export const createLoadProcessDefinitionsAction = createLoadingActionFunction(LOADING_PROPERTY);
export const createLoadProcessDefinitionsResultAction = createResultActionFunction(LOADING_PROPERTY);
export const createLoadProcessDefinitionsErrorAction = createErrorActionFunction(LOADING_PROPERTY);
