import {getRouter} from 'router';

const router = getRouter();

router
  .addRoutes(
    {
      name: 'login',
      url: '/login?name=:name&params=:params'
    },
    {
      name: 'default',
      url: '/'
    }
  );
