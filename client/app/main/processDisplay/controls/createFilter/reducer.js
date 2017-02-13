export const OPEN_DATE_FILTER_MODAL = 'OPEN_DATE_FILTER_MODAL';
export const CLOSE_DATE_FILTER_MODAL = 'CLOSE_DATE_FILTER_MODAL';

export function reducer(state = {open: false}, action) {
  if (action.type === OPEN_DATE_FILTER_MODAL) {
    return {
      ...state,
      open: true
    };
  }
  if (action.type === CLOSE_DATE_FILTER_MODAL) {
    return {
      ...state,
      open: false
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
