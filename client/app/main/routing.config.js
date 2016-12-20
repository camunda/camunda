import {getRouter} from 'router';

const router = getRouter();

router
  .addRoutes(
    {
      name: 'a',
      url: '/a/:a?b=:b'
    },
    {
      name: 'b',
      url: '/b/:b?c=:c'
    },
    {
      name: 'login',
      url: '/login?name=:name&params=:params'
    },
    {
      name: 'default',
      url: '/'
    }
  );
