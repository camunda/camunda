import {post} from 'modules/request';

export const login = async ({username, password}) => {
  const body = `username=${username}&password=${password}`;
  await post('/api/login', body, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    skipResponseInterceptor: true
  });
};
