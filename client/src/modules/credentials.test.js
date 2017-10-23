import {get, destroy, store, getToken} from './credentials';

it('store the login data in localstorage', () => {
  store('login');

  expect(localStorage.setItem.mock.calls[0][1]).toBe('"login"');
})

it('should retrieve the whole data with get', () => {
  localStorage.getItem.mockReturnValueOnce('"someData"');

  expect(get()).toBe('someData');
});

it('should return only the token with getToken', () => {
  localStorage.getItem.mockReturnValueOnce('{"token": "loginToken"}');

  expect(getToken()).toBe('loginToken');
});

it('should remove the login information', () => {
  destroy();

  expect(localStorage.removeItem).toHaveBeenCalled();
});