/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {Redirect, withRouter} from 'react-router-dom';

import {get, setResponseInterceptor} from 'modules/request';
import {useNotifications} from 'modules/notifications';
import {sessionValidationStore} from 'modules/stores/sessionValidation';
import {logoutUrl} from 'modules/api/header';

type Props = {
  location: {
    pathname: string;
    state?: {
      isLoggedIn?: boolean;
    };
  };
  children: React.ReactNode;
};

//@ts-expect-error
const Authentication: React.FC<Props> = (props) => {
  const [forceRedirect, setForceRedirect] = useState<boolean | null>(null);

  useEffect(() => {
    requestUserEndpoint().then((status: number) => {
      if (status === 401 || status === 403) {
        setForceRedirect(true);
      } else {
        sessionValidationStore.enableUserSession();
        setForceRedirect(false);
      }
      setResponseInterceptor(({status, url}: Response) => {
        if ((status === 401 || status === 403) && !url.includes(logoutUrl)) {
          setForceRedirect(true);
        }
      });
    });

    return () => {
      setResponseInterceptor(null);
    };
  }, []);

  const notifications = useNotifications();
  const requestUserEndpoint = () => {
    // use user endpoint to check for authentication
    return get('/api/authentications/user')
      .then((response) => response.status)
      .catch((error) => error.status);
  };

  const {state} = props.location;
  if (forceRedirect) {
    if (
      props.location.pathname !== '/' ||
      (props.location.pathname === '/' &&
        sessionValidationStore.state.isSessionValid)
    ) {
      notifications
        .displayNotification('info', {headline: 'Session expired'})
        .then((res) => {
          sessionValidationStore.disableUserSession(res);
        });
    }

    return (
      <Redirect
        to={{
          pathname: '/login',
          state: {referrer: props.location.pathname},
        }}
        push={true}
      />
    );
  } else if (state && state.isLoggedIn) {
    sessionValidationStore.enableUserSession();
    return props.children;
  } else if (state === undefined && forceRedirect === null) {
    // show empty page until we know if we need to redirect to login screen
    return null;
  } else {
    return props.children;
  }
};

// @ts-expect-error ts-migrate(2345) FIXME: Type 'unknown' is not assignable to type '{ isLogg... Remove this comment to see the full error message
export default withRouter(Authentication);
