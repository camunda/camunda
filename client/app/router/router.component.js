import {addChildren} from 'view-utils';

let lastRoute;

export function Router({children}) {
  return (node, eventBus) => {
    return [
      ({route}) => {
        lastRoute = route;
      }
    ].concat(
      addChildren(node, eventBus, children)
    );
  }
}

export function getLastRoute() {
  return lastRoute;
}
