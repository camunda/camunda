import {SELECT_PROCESS_DEFINITION} from 'main/processDisplay/controls/processDefinition/reducer';
import {SET_VIEW} from 'main/processDisplay/controls/view/reducer';

export const SET_ELEMENT = 'SET_ELEMENT';

export const reducer = (state = {}, action) => {
  if (action.type === SET_ELEMENT) {
    return {
      ...state,
      selection: {
        ...state.selection,
        [action.elementType]: action.id
      }
    };
  } else if (action.type === SELECT_PROCESS_DEFINITION || action.type === SET_VIEW) {
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
