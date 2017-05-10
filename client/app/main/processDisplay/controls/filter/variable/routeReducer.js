export const CREATE_VARIABLE_FILTER = 'CREATE_VARIABLE_FILTER';

export function routeReducer(state = [], action) {
  if (action.type === CREATE_VARIABLE_FILTER) {
    return [
      ...state,
      {
        type: 'variable',
        data: action.filter
      }
    ];
  }

  return state;
}

export function createCreateVariableFilterAction(filter) {
  return {
    type: CREATE_VARIABLE_FILTER,
    filter
  };
}
