import {CHANGE_ROUTE_ACTION} from 'router';

export const UNSET_ELEMENT = 'UNSET_ELEMENT';
export const TOGGLE_ELEMENT = 'TOGGLE_ELEMENT';

export const reducer = (state = {}, action) => {
  if (action.type === UNSET_ELEMENT) {
    return {
      ...state,
      [action.elementType]: null
    };
  } else if (action.type === TOGGLE_ELEMENT) {
    return {
      ...state,
      [action.elementType]: state[action.elementType] === action.id ? null : action.id
    };
  } else if (action.type === CHANGE_ROUTE_ACTION && action.route.params.view !== 'branch_analysis') {
    return {};
  }

  return state;
};

export function createUnsetElementAction(elementType) {
  return {
    type: UNSET_ELEMENT,
    elementType
  };
}

export function createToggleElementAction(id, elementType) {
  return {
    type: TOGGLE_ELEMENT,
    id,
    elementType
  };
}
