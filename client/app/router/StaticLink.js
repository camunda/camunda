import {getRouter} from './service';

const router = getRouter();

export function StaticLink({name, params, replace = false}) {
  return node => {
    const url = router.getUrl(name, params);

    node.addEventListener('click', (event) => {
      event.preventDefault();
      router.goTo(name, params, replace);
    });

    if (node.tagName === 'A') {
      node.setAttribute('href', url);
    }

    return [];
  };
}
