import {post, get} from 'modules/request';

export const logout = async () => {
  await post('/logout');
};

export const user = async () => {
  const response = await get('/authentications/user');
  return await response.json();
};
