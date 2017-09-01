import {
  addLoading, createLoadingActionFunction, createResultActionFunction,
  createErrorActionFunction, changeData
} from 'utils';

export const SET_VERSION = 'SET_VERSION';
export const SET_VERSION_XML = 'SET_VERSION_XML';
export const LOAD_PROCESSDEFINITIONS = 'LOAD_PROCESSDEFINITIONS';

export const LOADING_PROPERTY = 'processDefinitions';

export const reducer = addLoading((state, {type, previousId, version, xml}) => {
  if (state[LOADING_PROPERTY]) {
    if (type === SET_VERSION) {
      return changeData(state, LOADING_PROPERTY, ({list, ...rest}) => {
        return {
          ...rest,
          list: list.map(entry => {
            if (entry.current.id === previousId) {
              const current = entry.versions.filter(({version: otherVersion}) => otherVersion === version)[0];

              return {
                current,
                versions: entry.versions
              };
            }

            return entry;
          })
        };
      });
    }

    if (type === SET_VERSION_XML) {
      return changeData(state, LOADING_PROPERTY, ({list, ...rest}) => {
        return {
          ...rest,
          list: list.map(entry => {
            if (entry.current.id === previousId) {
              return {
                ...entry,
                versions: entry.versions.map(definition => {
                  if (definition.version === version) {
                    return {
                      ...definition,
                      bpmn20Xml: xml
                    };
                  }

                  return definition;
                })
              };
            }

            return entry;
          })
        };
      });
    }
  }

  return state;
}, LOADING_PROPERTY);

export function createSetVersionAction(previousId, version) {
  return {
    type: SET_VERSION,
    previousId,
    version
  };
}

export function createSetVersionXmlAction(previousId, version, xml) {
  return {
    type: SET_VERSION_XML,
    previousId,
    version,
    xml
  };
}

export const createLoadProcessDefinitionsAction = createLoadingActionFunction(LOADING_PROPERTY);
export const createLoadProcessDefinitionsResultAction = createResultActionFunction(LOADING_PROPERTY);
export const createLoadProcessDefinitionsErrorAction = createErrorActionFunction(LOADING_PROPERTY);
