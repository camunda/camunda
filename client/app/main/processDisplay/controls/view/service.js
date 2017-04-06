import {getLastRoute} from 'router';

export function getView() {
  const {params: {view}} = getLastRoute();

  return view;
}
