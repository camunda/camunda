const TOKENS_KEY = 'TOKENS';

function getTokens() {
  return JSON.parse(
    window.localStorage.getItem(TOKENS_KEY) || '[]'
  );
}

function saveTokens(tokens) {
  window.localStorage.setItem(
    TOKENS_KEY,
    JSON.stringify(tokens)
  );
}

export function authenticate(user, password) {
  if (user === 'admin' && password === 'admin') {
    const token = `TOKEN: ${Math.random()}`;
    const tokens = getTokens();

    tokens.push(token);

    saveTokens(tokens);

    return Promise.resolve(token);
  }

  return Promise.reject('Failed to authenticate');
}

export function checkToken(token) {
  const tokens = getTokens();

  if (tokens.indexOf(token) >= 0) {
    return Promise.resolve(true);
  }

  return Promise.reject(false);
}

window.clearAuthentication = () => {
  saveTokens([]);
};
