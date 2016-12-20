import {withChildren} from 'view-utils';
import {getLogin} from './loginRoot.component';
import {getRouter, getLastRoute} from 'router';

const router = getRouter();

export const Authenticated = withChildren(Authenticated_);

function Authenticated_({routeName}) {
  return () => {
    return () => {
      const login = getLogin();
      const {name, params} = getLastRoute();

      if (!login) {
        router.goTo(routeName, {name, params: JSON.stringify(params)}, true);
      }
    };
  };
}
