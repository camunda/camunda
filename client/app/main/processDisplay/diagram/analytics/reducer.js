import {SET_VIEW} from 'main/processDisplay/controls/view/reducer';

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
  } else if (action.type === SET_VIEW) {
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
