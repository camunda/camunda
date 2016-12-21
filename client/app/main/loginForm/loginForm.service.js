import {dispatchAction} from 'view-utils';
import {getRouter, getLastRoute} from 'router';
import {login} from '../login';
import {createChangeLoginPasswordAction, createChangeLoginUserAction, createLoginErrorAction} from './loginForm.reducer';

const router = getRouter();

export function performLogin(user, password) {
  return login(user, password)
    .then(() => {
      const {name, params: encodedParams} = getLastRoute().params;

      const params = JSON.parse(
        decodeURI(
          encodedParams
        )
      );

      dispatchAction(createLoginErrorAction(false));

      router.goTo(name, params);
    })
    .catch(() => {
      dispatchAction(createLoginErrorAction(true));
    });
}

export function changeUser({node: input}) {
  dispatchAction(createChangeLoginUserAction(input.value));
}

export function changePassword({node: input}) {
  dispatchAction(createChangeLoginPasswordAction(input.value));
}
