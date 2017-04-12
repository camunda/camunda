import {withSelector, updateOnlyWhenStateChanges} from 'view-utils';
import {getRouter} from './service';

const router = getRouter();

export const Link = withSelector(({replace = false}) => {
  return (node) => {
    let currentRoute;

    node.addEventListener('click', (event) => {
      if (currentRoute) {
        const {name, params} = currentRoute;

        event.preventDefault();
        router.goTo(name, params, replace);
      }
    });

    return updateOnlyWhenStateChanges((route) => {
      const {name, params} = route;
      const url = router.getUrl(name, params);

      currentRoute = route;

      if (node.tagName === 'A') {
        node.setAttribute('href', url);
      }
    });
  };
});
