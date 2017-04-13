import {dispatchAction} from 'view-utils';
import {getRouter, getLastRoute} from 'router';
import {login} from 'login';
import {createLoginErrorAction, createLoginInProgressAction} from './reducer';
import {addNotification} from 'notifications';

const router = getRouter();

export function performLogin(user, password) {
  dispatchAction(
    createLoginInProgressAction()
  );

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
    .catch(err => {
      addNotification({
        status: 'Could not login',
        text: err,
        isError: true
      });
      dispatchAction(createLoginErrorAction(true));
    });
}
