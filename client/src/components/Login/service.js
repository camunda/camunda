import {post} from 'request';
import {store} from 'credentials';

export async function login(username, password) {
  try {
    const response = await post('/api/authentication', {username, password});
    const token = await response.text();

    store({username, token});

    return token;
  } catch(e) {
    return false;
  }
}
