import {post} from 'request';

export async function login(username, password) {
  try {
    const response = await post('api/authentication', {username, password});
    const token = await response.text();

    return {token};
  } catch (e) {
    return await e.json();
  }
}
