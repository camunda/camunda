export const SET_VIEW = 'SET_VIEW';

export function reducer(state = 'none', {type, mode}) {
  if (type === SET_VIEW) {
    return mode;
  }
  return state;
}

export function createSetViewAction(mode) {
  return {
    type: SET_VIEW,
    mode
  };
}
