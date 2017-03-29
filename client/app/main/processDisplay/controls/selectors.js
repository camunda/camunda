import {includes} from 'view-utils';

export function isViewSelected({view}, targetView) {
  if (typeof targetView === 'string') {
    targetView = [targetView];
  }

  return includes(targetView, view);
}

export function getView({view}) {
  return view;
}
