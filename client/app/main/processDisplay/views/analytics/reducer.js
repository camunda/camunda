import {CHANGE_ROUTE_ACTION} from 'router';

export const UNSET_ELEMENT = 'UNSET_ELEMENT';
export const TOGGLE_ELEMENT = 'TOGGLE_ELEMENT';
export const ADD_HIGHLIGHT = 'ADD_HIGHLIGHT';
export const REMOVE_HIGHLIGHTS = 'REMOVE_HIGHLIGHTS';

export const reducer = (state = {selection: {}, hover: {}}, action) => {
  if (action.type === UNSET_ELEMENT) {
    const selection = {
      ...state.selection
    };

    delete selection[action.elementType];

    return {
      ...state,
      selection
    };
  }

  if (action.type === TOGGLE_ELEMENT) {
    const selection = {
      ...state.selection
    };

    if (state.selection[action.elementType] === action.id) {
      delete selection[action.elementType];
    } else {
      selection[action.elementType] = action.id;
    }

    return {
      ...state,
      selection
    };
  }

  if (action.type === CHANGE_ROUTE_ACTION && action.route.params.view !== 'branch_analysis') {
    return {
      ...state,
      selection: {},
      hover: {}
    };
  }

  if (action.type === ADD_HIGHLIGHT) {
    const {elementType,  elementId} = action;

    return {
      ...state,
      hover: {
        ...state.hover,
        [elementType]: {
          elementType,
          elementId
        }
      }
    };
  }

  if (action.type === REMOVE_HIGHLIGHTS) {
    return {
      ...state,
      hover: {}
    };
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

export function createAddHighlightAction(elementType, elementId) {
  return {
    type: ADD_HIGHLIGHT,
    elementType,
    elementId
  };
}

export function createRemoveHighlightsAction() {
  return {
    type: REMOVE_HIGHLIGHTS
  };
}
