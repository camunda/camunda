import {includes} from 'view-utils';
import {getView} from './view';

export function isViewSelected(targetView) {
  const view = getView();

  if (typeof targetView === 'string') {
    targetView = [targetView];
  }

  return includes(targetView, view);
}
