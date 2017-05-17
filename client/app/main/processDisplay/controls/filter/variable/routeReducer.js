export const CREATE_VARIABLE_FILTER = 'CREATE_VARIABLE_FILTER';

export function routeReducer(state = [], action) {
  if (action.type === CREATE_VARIABLE_FILTER) {
    const {name, type, operator, values} = action.filter;

    return [
      ...state,
      {
        type: 'variable',
        data: [name, type, operator, values]
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
