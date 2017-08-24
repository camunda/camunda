import isEqual from 'lodash.isequal';
import {pipeReducers} from 'utils';
import {routeReducer as dateReducer} from './date';
import {routeReducer as variableReducer} from './variable';
import {routeReducer as executedNodeReducer} from './executedNode';

export const DELETE_FILTER = 'DELETE_FILTER';

export function reducer(state = [], action) {
  return pipeReducers(
    dateReducer,
    variableReducer,
    executedNodeReducer,
    (state, action) => {
      if (action.type === DELETE_FILTER) {
        return state.filter(({data}) => {
          return !isEqual(data, action.filter);
        });
      }

      return state;
    }
  )(state, action);
}

export function createDeleteFilterAction(filter) {
  return {
    type: DELETE_FILTER,
    filter
  };
}
