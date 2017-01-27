export const INITIAL_STATE = 'INITIAL';
export const LOADING_STATE = 'LOADING';
export const LOADED_STATE = 'LOADED';

export function addLoading(next, ...properties) {
  const loadTypes = getLoadTypes(properties);
  const loadedTypes = getLoadedTypes(properties);

  return (state = {}, action) => {
    const loadIdx = loadTypes.indexOf(action.type);
    const loadedIdx = loadedTypes.indexOf(action.type);

    properties.forEach(prop => {
      if (!state[prop]) {
        state[prop] = {state: INITIAL_STATE};
      }
    });

    if (loadIdx !== -1) {
      return {
        ...state,
        [properties[loadIdx]]: {
          state: LOADING_STATE
        }
      };
    } else if (loadedIdx !== -1) {
      return {
        ...state,
        [properties[loadedIdx]]: {
          state: LOADED_STATE,
          data: action.result
        }
      };
    }

    return next(state, action);
  };
}

function getLoadTypes(properties) {
  return properties.map(prop => 'LOAD_' + prop.toUpperCase());
}
function getLoadedTypes(properties) {
  return properties.map(prop => 'LOADED_' + prop.toUpperCase());
}

export function createLoadingAction(name) {
  return {
    type: 'LOAD_' + name.toUpperCase()
  };
}

export function createResultAction(name, result) {
  return {
    type: 'LOADED_' + name.toUpperCase(),
    result
  };
}
