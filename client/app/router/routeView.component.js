import {getLastRoute} from './component';
import {Children, jsx} from 'view-utils';

export function RouteView({name, children}) {
  const template = <Children children={children}></Children>;

  template.predicate = shouldDisplayView;

  return template;

  function shouldDisplayView() {
    const lastRoute = getLastRoute();

    if (!lastRoute) {
      return false;
    }

    if (name instanceof RegExp) {
      return name.test(lastRoute.name);
    }

    return name === lastRoute.name;
  }
}
