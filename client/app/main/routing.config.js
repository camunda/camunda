import {getRouter} from 'router';

const router = getRouter();

router.addRoutes(
  {
    name: 'login',
    url: '/login?name=:name&params=:params'
  },
  {
    name: 'processDisplay',
    url: '/process/:definition/:view',
    defaults: {
      view: 'none',
      filter: '~(~)'
    }
  },
  {
    name: 'default',
    url: '/',
    test: url => (/\/$|\/index.html$/g).test(url) && {}
  }
);
