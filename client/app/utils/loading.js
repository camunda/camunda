import {includes, DESTROY_EVENT} from 'view-utils';

export const INITIAL_STATE = 'INITIAL';
export const LOADING_STATE = 'LOADING';
export const LOADED_STATE = 'LOADED';
export const ERROR_STATE = 'ERROR';

// This function is similar to addLoading, but has a bit different use case.
// It creates reducer that has no other functionality but data loading,
// so it does not need to create properties for that. And it can be used inside
// combineReducers without creating strange state tree.
export function createLoadingReducer(name) {
  const loadTypes = getTypes('LOAD', [name]);
  const loadedTypes = getTypes('LOADED', [name]);
  const resetTypes = getTypes('RESET', [name]);
  const errorTypes = getTypes('ERROR', [name]);

  return (state, action) => {
    if (!state) {
      return createIntialState();
    }

    if (includes(loadTypes, action.type)) {
      return createLoadingState();
    } else if (includes(loadedTypes, action.type)) {
      return createLoadedState(action.result);
    } else if (includes(resetTypes, action.type)) {
      return createIntialState();
    } else if (includes(errorTypes, action.type)) {
      return createErrorState(action.error);
    }

    return state;
  };
}

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
          [prop]: createIntialState()
        };
      }

      return state;
    }, newState);

    if (includes(loadTypes, action.type)) {
      return {
        ...newState,
        [action.property]: createLoadingState()
      };
    } else if (includes(loadedTypes, action.type)) {
      return {
        ...newState,
        [action.property]: createLoadedState(action.result)
      };
    } else if (includes(resetTypes, action.type)) {
      return {
        ...newState,
        [action.property]: createIntialState()
      };
    } else if (includes(errorTypes, action.type)) {
      return {
        ...newState,
        [action.property]: createErrorState(action.error)
      };
    }

    return newState;
  };
}

function createLoadingState() {
  return createLoaderState(LOADING_STATE);
}

function createLoadedState(data) {
  return createLoaderState(LOADED_STATE, {data});
}

function createIntialState() {
  return createLoaderState(INITIAL_STATE);
}

function createErrorState(error) {
  return createLoaderState(ERROR_STATE, {error});
}

function createLoaderState(state, properties = {}) {
  return  {
    state,
    ...properties
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
