import {onModuleLoaded} from './registry.service';

export function createDynamicReducer(module) {
  let reducer;

  onModuleLoaded(module)
    .then(({reducer: _reducer}) => {
      reducer = _reducer;
    });

  return ({loading, ...state} = {}, action) => {
    if (!reducer) {
      return {
        ...state,
        loading: true
      };
    } else {
      return {
        ...state,
        loading: false,
        ...reducer(state, action)
      };
    }
  };
}
