export const SET_TARGET_VALUE = 'SET_TARGET_VALUE';

export const reducer = (state = {data: {}}, action) => {
  if (action.type === SET_TARGET_VALUE) {
    const newState = {
      ...state,
      data: {
        ...state.data,
        [action.element]: action.value
      }
    };

    if (!action.value) {
      delete newState.data[action.element];
    }

    return newState;
  }

  return state;
};

export function createSetTargetValueAction(element, value) {
  return {
    type: SET_TARGET_VALUE,
    element: element.businessObject.id,
    value
  };
}
