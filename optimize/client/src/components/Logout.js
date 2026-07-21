/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {withRouter} from 'react-router-dom';
import {post} from 'request';
import {withErrorHandling} from 'HOC';
import {addNotification} from 'notifications';
import {t} from 'translation';

export function Logout({mightFail, history}) {
  useEffect(() => {
    (async () => {
      await mightFail(
        // SPIKE (ADR-0036): log out via CSL's server-side /logout endpoint (aligned with OC).
        // For fetch/XHR it responds 200 {"url": <IdP end-session URL>} (or 204 when the IdP has
        // no end-session endpoint). We navigate the browser to that URL ourselves, because a 302
        // from fetch cannot drive a cross-origin top-level navigation. This both invalidates the
        // server session and ends the Keycloak SSO session, so the user is not logged back in.
        post('/logout'),
        async (response) => {
          addNotification({text: t('navigation.logoutSuccess')});
          if (response.status === 200) {
            const {url} = await response.json();
            window.location.href = url;
            return;
          }
          // 204: no IdP end-session endpoint. The local session is gone; go to the root so the
          // webapp chain re-initiates login.
          window.location.href = '/';
        },
        () => {
          addNotification({text: t('navigation.logoutFailed'), type: 'error'});
          history.replace('/');
        }
      );
    })();
  }, [mightFail, history]);

  return null;
}

export default withRouter(withErrorHandling(Logout));
