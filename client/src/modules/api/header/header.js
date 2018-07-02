import {get, post} from 'modules/request';

export const logout = async () => {
  await post('/api/logout');
};

export const fetchUser = async () => {
  const response = await get('/api/authentications/user');
  return await response.json();
};
