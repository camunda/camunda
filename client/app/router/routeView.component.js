import {getLastRoute} from './router.component';
import {Match, Case, Children, jsx} from 'view-utils';

export function RouteView({name, children}) {
  return <div>
    <Match>
      <Case predicate={shouldDisplayView}>
        <Children children={children}></Children>
      </Case>
    </Match>
  </div>;

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
