import {addLoading, createLoadingActionFunction, createResultActionFunction, createErrorActionFunction} from 'utils';
import {CHANGE_ROUTE_ACTION} from 'router';
import {operatorCanHaveMultipleValues} from './service';

export const SELECT_VARIABLE_IDX = 'SELECT_VARIABLE_IDX';
export const SET_OPERATOR = 'SET_OPERATOR';
export const SET_VALUE = 'SET_VALUE';
export const ADD_VALUE = 'ADD_VALUE';
export const REMOVE_VALUE = 'REMOVE_VALUE';

export const reducer = addLoading((state = {operator: '=', value: []}, action) => {
  if (action.type === SELECT_VARIABLE_IDX) {
    return {
      ...state,
      selectedIdx: action.idx,
      operator: '=',
      value: []
    };
  }
  if (action.type === SET_OPERATOR) {
    let newValue = state.value;

    if (!operatorCanHaveMultipleValues(action.operator)) {
      newValue = [state.value[0]];
    }

    return {
      ...state,
      operator: action.operator,
      value: newValue
    };
  }
  if (action.type === SET_VALUE) {
    return {
      ...state,
      value: [action.value]
    };
  }
  if (action.type === ADD_VALUE) {
    const valueCopy = [...state.value];

    valueCopy.push(action.value);

    return {
      ...state,
      value: valueCopy
    };
  }
  if (action.type === REMOVE_VALUE) {
    const valueCopy = [...state.value];

    valueCopy.splice(valueCopy.indexOf(action.value), 1);

    return {
      ...state,
      value: valueCopy
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
