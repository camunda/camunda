import {includes, DESTROY_EVENT} from 'view-utils';

export const INITIAL_STATE = 'INITIAL';
export const LOADING_STATE = 'LOADING';
export const LOADED_STATE = 'LOADED';
export const ERROR_STATE = 'ERROR';

export function addLoading(next, ...properties) {
  const loadTypes = getTypes('LOAD', properties);
  const loadedTypes = getTypes('LOADED', properties);
  const resetTypes = getTypes('RESET', properties);
  const errorTypes = getTypes('ERROR', properties);

  return (state, action) => {
    let newState = next(state, action) || {};

    newState = properties.reduce((state, prop) => {
      if (!state[prop]) {
        return {
          ...state,
          [prop]: {state: INITIAL_STATE}
        };
      }

      return state;
    }, newState);

    if (includes(loadTypes, action.type)) {
      return {
        ...newState,
        [action.property]: {
          state: LOADING_STATE
        }
      };
    } else if (includes(loadedTypes, action.type)) {
      return {
        ...newState,
        [action.property]: {
          state: LOADED_STATE,
          data: action.result
        }
      };
    } else if (includes(resetTypes, action.type)) {
      return {
        ...newState,
        [action.property]: {
          state: INITIAL_STATE
        }
      };
    } else if (includes(errorTypes, action.type)) {
      return {
        ...newState,
        [action.property]: {
          state: ERROR_STATE,
          error: action.error
        }
      };
    }

    return newState;
  };
}

function getTypes(type, properties) {
  return properties.map(
    getType.bind(null, type)
  );
}

function getType(type, prop) {
  return `${type.toUpperCase()}_${prop.toUpperCase()}`;
}

export const createResetActionFunction = createTypedActionFunctionCreator('RESET');
export const createLoadingActionFunction = createTypedActionFunctionCreator('LOAD');
export const createResultActionFunction = createTypedActionFunctionCreator('LOADED', result => {
  return {result};
});
export const createErrorActionFunction = createTypedActionFunctionCreator('ERROR', error => {
  return {error};
});

function createTypedActionFunctionCreator(typePrefix, create = empty) {
  return name => {
    const type = getType(typePrefix, name);

    return (...args) => {
      const action = create(...args);

      return {
        ...action,
        property: name,
        type
      };
    };
  };
}

function empty() {
  return {};
}

export function addDestroyEventCleanUp(eventsBus, dispatch, ...names) {
  const resetActionCreators = names.map(createResetActionFunction);

  return eventsBus.on(DESTROY_EVENT, () => {
    resetActionCreators.forEach(createAction => {
      dispatch(createAction());
    });
  });
}

export function isInitial({state}) {
  return state === INITIAL_STATE;
}

export function isLoading({state}) {
  return state === LOADING_STATE;
}

export function isLoaded({state}) {
  return state === LOADED_STATE;
}

export function isError({state}) {
  return state === ERROR_STATE;
}
