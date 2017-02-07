export const HOVER_ELEMENT = 'HOVER_ELEMENT';

export const reducer = (state = {}, action) => {
  if (action.type === HOVER_ELEMENT) {
    return {
      ...state,
      hovered: action.element
    };
  }
  return state;
};

export function createHoverElementAction(element) {
  return {
    type: HOVER_ELEMENT,
    element: element.id
  };
}
