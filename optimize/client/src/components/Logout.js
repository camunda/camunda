/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
