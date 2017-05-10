import isEqual from 'lodash.isequal';
import {routeReducer as dateReducer} from './date';
import {routeReducer as variableReducer} from './variable';

export const DELETE_FILTER = 'DELETE_FILTER';

export function reducer(state = [], action) {
  let newState;

  newState = dateReducer(state, action);
  newState = variableReducer(newState, action);

  if (action.type === DELETE_FILTER) {
    return newState.filter(({data}) => {
      return !isEqual(data, action.filter);
    });
  }

  return newState;
}

export function createDeleteFilterAction(filter) {
  return {
    type: DELETE_FILTER,
    filter
  };
}
