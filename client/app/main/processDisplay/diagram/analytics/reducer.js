import {CHANGE_ROUTE_ACTION} from 'router';

export const SET_ELEMENT = 'SET_ELEMENT';

export const reducer = (state = {selection: {}}, action) => {
  if (action.type === SET_ELEMENT) {
    return {
      ...state,
      selection: {
        ...state.selection,
        [action.elementType]: action.id
      }
    };
  } else if (action.type === CHANGE_ROUTE_ACTION && action.route.params.view !== 'branch_analysis') {
    return {
      ...state,
      selection: {}
    };
  }

  return state;
};

export function createSetElementAction(id, elementType) {
  return {
    type: SET_ELEMENT,
    id,
    elementType
  };
}
