import {withChildren, runUpdate} from 'view-utils';

let lastRoute;

export const Router = withChildren(RouterRoot);

function RouterRoot({routerProperty}) {
  return (node, eventBus) => {
    return ({[routerProperty]: {route}}) => {
      lastRoute = route;
    };
  }
}

export function getLastRoute() {
  return lastRoute;
}
