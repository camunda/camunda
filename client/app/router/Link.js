import {withSelector, updateOnlyWhenStateChanges} from 'view-utils';
import {getRouter} from './service';

const router = getRouter();

export const Link = withSelector(({replace = false}) => {
  return (node) => {
    return updateOnlyWhenStateChanges(({name, params}) => {
      const url = router.getUrl(name, params);

      node.addEventListener('click', (event) => {
        event.preventDefault();
        router.goTo(name, params, replace);
      });

      if (node.tagName === 'A') {
        node.setAttribute('href', url);
      }
    });
  };
});
