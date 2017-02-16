export const OPEN_DATE_FILTER_MODAL = 'OPEN_DATE_FILTER_MODAL';
export const CLOSE_DATE_FILTER_MODAL = 'CLOSE_DATE_FILTER_MODAL';
export const CREATE_START_DATE_FILTER = 'CREATE_START_DATE_FILTER';
export const DELETE_FILTER = 'DELETE_FILTER';

export function reducer(state = [], action) {
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
  if (action.type === DELETE_FILTER) {
    return state.filter(({data}) => {
      return data !== action.filter;
    });
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

export function createDeleteFilterAction(filter) {
  return {
    type: DELETE_FILTER,
    filter
  };
}
