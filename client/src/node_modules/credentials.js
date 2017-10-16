const key = 'LOGIN_KEY';

export function get() {
  return JSON.parse(localStorage.getItem(key));
}

export function destroy() {
  return localStorage.removeItem(key);
}

export function store(data) {
  return localStorage.setItem(key, JSON.stringify(data));
}

export function getToken() {
  const data = get();
  return data && data.token;
}