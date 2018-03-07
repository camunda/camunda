const key = 'LOGIN_KEY';

export function get() {
  return JSON.parse(localStorage.getItem(key));
}

export function destroy() {
  document.cookie = 'X-Optimize-Authorization=';
  return localStorage.removeItem(key);
}

export function store(data) {
  document.cookie = `X-Optimize-Authorization=Bearer ${data.token};path=/`;
  return localStorage.setItem(key, JSON.stringify(data));
}

export function getToken() {
  const data = get();
  return data && data.token;
}
