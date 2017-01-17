import {getRouter, getLastRoute} from 'router';
import {Children, runUpdate, jsx} from 'view-utils';
import {getLogin} from './loginRoot.component';

const router = getRouter();

export function Authenticated({routeName, children}) {
  return (node, eventsBus) => {
    const target = document.createElement('div');
    let update;

    node.appendChild(target);

    return (state) => {
      const login = getLogin();
      const {name, params} = getLastRoute();

      if (!login) {
        router.goTo(routeName, {name, params: JSON.stringify(params)}, true);
      } else if (!login.check) {
        if (!update) {
          const template = <Children children={children} />;

          update = template(target, eventsBus);
          runUpdate(update, state);
        } else {
          runUpdate(update, state);
        }
      }
    };
  };
}
