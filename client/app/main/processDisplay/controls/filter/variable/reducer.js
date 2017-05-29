import {addLoading, createLoadingActionFunction, createResultActionFunction, createErrorActionFunction} from 'utils';
import {CHANGE_ROUTE_ACTION} from 'router';

export const SELECT_VARIABLE_IDX = 'SELECT_VARIABLE_IDX';
export const SET_OPERATOR = 'SET_OPERATOR';
export const SET_VALUE = 'SET_VALUE';

export const reducer = addLoading((state = {operator: '='}, action) => {
  if (action.type === SELECT_VARIABLE_IDX) {
    return {
      ...state,
      selectedIdx: action.idx,
      operator: '=',
      value: ''
    };
  }
  if (action.type === SET_OPERATOR) {
    return {
      ...state,
      operator: action.operator
    };
  }
  if (action.type === SET_VALUE) {
    return {
      ...state,
      value: action.value
    };
  }
  if (action.type === CHANGE_ROUTE_ACTION && !action.route.params.definition) {
    return {};
  }

  return state;
}, 'variables');

export const createLoadingVariablesAction = createLoadingActionFunction('variables');
export const createLoadingVariablesResultAction = createResultActionFunction('variables');
export const createLoadingVariablesErrorAction = createErrorActionFunction('variables');

export function createSelectVariableIdxAction(idx) {
  return {
    type: SELECT_VARIABLE_IDX,
    idx
  };
}

export function createSetOperatorAction(operator) {
  return {
    type: SET_OPERATOR,
    operator
  };
}

export function createSetValueAction(value) {
  return {
    type: SET_VALUE,
    value
  };
}
