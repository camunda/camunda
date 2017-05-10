export const CREATE_START_DATE_FILTER = 'CREATE_START_DATE_FILTER';

export function routeReducer(state = [], action) {
  if (action.type === CREATE_START_DATE_FILTER) {
    // remove old date filter
    const newState = state.filter(({type}) => {
      return type !== 'startDate';
    });

    return [
      ...newState,
      {
        type: 'startDate',
        data: {
          start: action.start,
          end: action.end
        }
      }
    ];
  }

  return state;
}

export function createCreateStartDateFilterAction(start, end) {
  return {
    type: CREATE_START_DATE_FILTER,
    start,
    end
  };
}
