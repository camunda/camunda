/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {getOnboardingConfig} from 'config';

import {withUser} from 'HOC';

export function Onboarding({user}) {
  const location = useLocation();
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    (async () => {
      const {enabled, appCuesScriptUrl} = await getOnboardingConfig();
      if (!enabled || !appCuesScriptUrl) {
        return;
      }
      await enableAppCues(appCuesScriptUrl);
      setInitialized(true);
    })();
  }, []);

  useEffect(() => {
    if (user) {
      window.Appcues?.identify(user.id);
    }
  }, [user]);

  useEffect(() => {
    if (initialized && user?.id) {
      window.Appcues?.page();
    }
  }, [location, initialized, user?.id]);

  return null;
}

async function enableAppCues(appCuesScriptUrl) {
  return new Promise((resolve) => {
    // ensure the script is only runs once as otherwise AppCues might get loaded multiple times causing warn logs
    const appCuesScriptElement = document.createElement('script');
    appCuesScriptElement.src = appCuesScriptUrl;
    document.head.appendChild(appCuesScriptElement);
    appCuesScriptElement.onload = resolve;
  });
}

export default withUser(Onboarding);
