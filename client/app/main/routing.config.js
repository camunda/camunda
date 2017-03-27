import {getRouter} from 'router';

const router = getRouter();

router.addRoutes(
  {
    name: 'login',
    url: '/login?name=:name&params=:params'
  },
  {
    name: 'processDisplay',
    url: '/processDisplay?definition=:definition'
  },
  {
    name: 'default',
    url: '/',
    test: url => (/\/$|\/index.html$/g).test(url) && {}
  }
);
