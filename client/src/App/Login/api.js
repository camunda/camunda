import {post} from 'modules/request';

export const login = async ({username, password}) => {
  const body = `username=${username}&password=${password}`;
  await post('/login', body, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  });
};
