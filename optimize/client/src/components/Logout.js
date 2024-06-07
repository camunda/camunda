/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {withRouter} from 'react-router-dom';
import {get} from 'request';
import {withErrorHandling} from 'HOC';
import {addNotification} from 'notifications';
import {t} from 'translation';

export function Logout({mightFail, history}) {
  useEffect(() => {
    (async () => {
      await mightFail(
        get('api/authentication/logout'),
        () => addNotification({text: t('navigation.logoutSuccess')}),
        () => addNotification({text: t('navigation.logoutFailed'), type: 'error'})
      );
      history.replace('/');
    })();
  }, [mightFail, history]);

  return null;
}

export default withRouter(withErrorHandling(Logout));
