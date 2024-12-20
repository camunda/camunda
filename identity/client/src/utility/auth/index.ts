import {baseUrl} from "src/configuration";

export const LOGIN_PATH = `${baseUrl}/login`;

export function getLoginPath(next?: string) {
  return next ? `${LOGIN_PATH}?next=${next}` : LOGIN_PATH;
}

export function redirectToLogin() {
  window.location.href = getLoginPath(window.location.pathname);
}

export function login(username: string, password: string): Promise<boolean> {
  const data = new FormData();
  data.set("username", username);
  data.set("password", password);
  return fetch("/login", {
    method: "post",
    body: data,
  })
    .then((response: Response) => {
      if (response.status < 400) {
        return true;
      }
      return false;
    })
    .catch((e) => {
      console.log(e);
      return false;
    });
}

export function logout() {
  const data = new FormData();
  return fetch("/logout", {
    method: "post",
    body: data,
  })
    .then((response: Response) => {
      if (response.status < 400) {
        window.location.href = `${LOGIN_PATH}?next=${window.location.pathname}`;
      }
    })
    .catch((e) => {
      console.log(e);
    });
}
