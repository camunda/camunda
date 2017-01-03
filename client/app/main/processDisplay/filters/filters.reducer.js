export const CHANGE_FILTER_ACTION = 'CHANGE_FILTER_ACTION';

export function reducer(state = {filters: {}}, action) {
  switch (action.type) {
    case CHANGE_FILTER_ACTION:
      return {
        ...state,
        filters: {
          ...state.filters,
          [action.filter]: action.value
        }
      };
  }

  return state;
}

export function createChangeFilterAction(filter, value) {
  return {
    type: CHANGE_FILTER_ACTION,
    filter,
    value
  };
}
