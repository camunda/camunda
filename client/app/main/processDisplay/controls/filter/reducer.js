export const OPEN_DATE_FILTER_MODAL = 'OPEN_DATE_FILTER_MODAL';
export const CLOSE_DATE_FILTER_MODAL = 'CLOSE_DATE_FILTER_MODAL';
export const CREATE_START_DATE_FILTER = 'CREATE_START_DATE_FILTER';

export function reducer(state = {createModal: {open: false}, query: []}, action) {
  if (action.type === OPEN_DATE_FILTER_MODAL) {
    return {
      ...state,
      createModal: {
        open: true
      }
    };
  }
  if (action.type === CLOSE_DATE_FILTER_MODAL) {
    return {
      ...state,
      createModal: {
        open: false
      }
    };
  }
  if (action.type === CREATE_START_DATE_FILTER) {
    return {
      ...state,
      query: [{
        type: 'startDate',
        data: {
          start: action.start,
          end: action.end
        }
      }]
    };
  }

  return state;
}

export function createOpenDateFilterModalAction() {
  return {
    type: OPEN_DATE_FILTER_MODAL
  };
}

export function createCloseDateFilterModalAction() {
  return {
    type: CLOSE_DATE_FILTER_MODAL
  };
}

export function createCreateStartDateFilterAction(start, end) {
  return {
    type: CREATE_START_DATE_FILTER,
    start,
    end
  };
}
