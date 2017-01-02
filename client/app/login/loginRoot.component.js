import {withChildren} from 'view-utils';

let loginCache = null;

export const LoginRoot = withChildren(LoginRoot_);

function LoginRoot_() {
  return () => {
    return ({login}) => {
      loginCache = login;
    }
  }
}

export function getLogin() {
  return loginCache;
}
