import {post} from 'modules/request';

export const requestLogin = async data => {
  const body = `username=${data.username}&password=${data.password}`;
  const user = await post('/login', body, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  });
  console.log('user: ', user);
  return user;
};
