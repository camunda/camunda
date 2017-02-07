import {withChildren, withSelector} from 'view-utils';

let lastRoute;

export const Router = withChildren(
  withSelector(RouterRoot)
);

function RouterRoot() {
  return () => {
    return ({route}) => {
      lastRoute = route;
    };
  };
}

export function getLastRoute() {
  return lastRoute;
}
