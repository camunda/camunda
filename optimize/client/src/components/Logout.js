/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {withRouter} from 'react-router-dom';
import {get, post} from 'request';
import {withErrorHandling} from 'HOC';
import {addNotification} from 'notifications';
import {isCslEnabled} from 'config';
import {t} from 'translation';

export function Logout({mightFail, history}) {
  useEffect(() => {
    (async () => {
      if (await isCslEnabled()) {
        // CSL mode (ADR-0038): log out via the CSL server-side logout endpoint (aligned with OC),
        // which invalidates the session and triggers the IdP end-session. For fetch/XHR it responds
        // 200 {"url": <IdP end-session URL>}, or 204 when the IdP has no end-session endpoint. We
        // drive the top-level navigation ourselves, because a 302 from fetch cannot follow a
        // cross-origin redirect. The URL is relative so it resolves under Optimize's servlet
        // context path (e.g. /<clusterId> on CCSaaS), like every other request the SPA makes.
        await mightFail(
          post('logout'),
          async (response) => {
            // No success notification here: both branches below do a full top-level
            // navigation, which discards the in-memory notification before it can render.
            if (response.status === 200) {
              // Navigate to the IdP end-session URL from the response body. Fall back to the
              // app root if it is missing or the body is not parseable, so we never navigate
              // to "<contextPath>/undefined" and the user still lands back in the login flow.
              let url;
              try {
                ({url} = await response.json());
              } catch {
                url = undefined;
              }
              window.location.href = url || '.';
              return;
            }
            // 204: no IdP end-session endpoint. Go to the app root (relative to the context
            // path) so the webapp chain re-initiates login. We navigate to '.' rather than
            // reload() because Optimize's HashRouter serves /logout as a route: a reload would
            // remount this component and re-POST logout against an already-invalidated session.
            window.location.href = '.';
          },
          () => {
            addNotification({text: t('navigation.logoutFailed'), type: 'error'});
            history.replace('/');
          }
        );
        return;
      }

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
