import {post} from 'modules/request';

export const login = async data => {
  const body = `username=${data.username}&password=${data.password}`;
  await post('/login', body, {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    }
  });
};
