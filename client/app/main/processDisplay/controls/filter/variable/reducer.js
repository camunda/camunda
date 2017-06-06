import {addLoading, createLoadingActionFunction, createResultActionFunction, createErrorActionFunction} from 'utils';
import {CHANGE_ROUTE_ACTION} from 'router';
import {operatorCanHaveMultipleValues} from './service';

export const SELECT_VARIABLE_IDX = 'SELECT_VARIABLE_IDX';
export const SET_OPERATOR = 'SET_OPERATOR';
export const SET_VALUE = 'SET_VALUE';
export const ADD_VALUE = 'ADD_VALUE';
export const REMOVE_VALUE = 'REMOVE_VALUE';

export const reducer = addLoading((state = {operator: '=', values: []}, action) => {
  if (action.type === SELECT_VARIABLE_IDX) {
    return {
      ...state,
      selectedIdx: action.idx,
      operator: '=',
      values: []
    };
  }
  if (action.type === SET_OPERATOR) {
    let newValues = state.values;

    if (!operatorCanHaveMultipleValues(action.operator)) {
      newValues = [state.values[0]];
    }

    return {
      ...state,
      operator: action.operator,
      values: newValues
    };
  }
  if (action.type === SET_VALUE) {
    const valuesCopy = [...state.values];

    valuesCopy[action.idx] = action.value;

    return {
      ...state,
      values: valuesCopy
    };
  }
  if (action.type === ADD_VALUE) {
    return {
      ...state,
      values: state.values.concat(action.value)
    };
  }
  if (action.type === REMOVE_VALUE) {
    return {
      ...state,
      values: state.values.filter(value => value !== action.value)
    };
  }
  if (action.type === CHANGE_ROUTE_ACTION && !action.route.params.definition) {
    return {
      operator: '=',
      values: []
    };
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

export function createSetValueAction(value, idx) {
  return {
    type: SET_VALUE,
    value,
    idx: idx || 0
  };
}

export function createAddValueAction(value) {
  return {
    type: ADD_VALUE,
    value
  };
}

export function createRemoveValueAction(value) {
  return {
    type: REMOVE_VALUE,
    value
  };
}
