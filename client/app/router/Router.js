import {withChildren, withSelector} from 'view-utils';
import {getRouter} from './service';

const router = getRouter();

let lastRoute;

export const Router = withChildren(
  withSelector(RouterRoot)
);

function RouterRoot() {
  return () => {
    return ({route}) => {
      lastRoute = route;
      router.fireHistoryListeners(route);
    };
  };
}

export function getLastRoute() {
  return lastRoute;
}
