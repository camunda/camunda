export const INITIAL_STATE = 'INITIAL';
export const LOADING_STATE = 'LOADING';
export const LOADED_STATE = 'LOADED';

export function addLoading(next, ...properties) {
  const loadTypes = getLoadTypes(properties);
  const loadedTypes = getLoadedTypes(properties);

  return (state = {}, action) => {
    const loadIdx = loadTypes.indexOf(action.type);
    const loadedIdx = loadedTypes.indexOf(action.type);

    const newState = {...state};

    properties.forEach(prop => {
      if (!newState[prop]) {
        newState[prop] = {state: INITIAL_STATE};
      }
    });

    if (loadIdx !== -1) {
      return {
        ...newState,
        [properties[loadIdx]]: {
          state: LOADING_STATE
        }
      };
    } else if (loadedIdx !== -1) {
      return {
        ...newState,
        [properties[loadedIdx]]: {
          state: LOADED_STATE,
          data: action.result
        }
      };
    }

    return next(newState, action);
  };
}

function getLoadTypes(properties) {
  return properties.map(prop => 'LOAD_' + prop.toUpperCase());
}
function getLoadedTypes(properties) {
  return properties.map(prop => 'LOADED_' + prop.toUpperCase());
}

export function createLoadingActionFunction(name) {
  return () => {
    return {
      type: 'LOAD_' + name.toUpperCase()
    };
  };
}

export function createResultActionFunction(name) {
  return result => {
    return {
      type: 'LOADED_' + name.toUpperCase(),
      result
    };
  };
}
