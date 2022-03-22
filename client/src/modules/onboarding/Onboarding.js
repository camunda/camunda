/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {getOnboardingConfig} from 'config';

import {withUser} from 'HOC';

export function Onboarding({getUser}) {
  const location = useLocation();
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    if (initialized) {
      window.Appcues?.page();
    }
  }, [location, initialized]);

  useEffect(() => {
    (async () => {
      const {enabled, appCuesScriptUrl} = await getOnboardingConfig();
      if (!enabled || !appCuesScriptUrl || !(await enableAppCues(appCuesScriptUrl))) {
        return;
      }
      const user = await getUser();
      window.Appcues?.identify(user.id);
      setInitialized(true);
    })();
  }, [getUser]);

  return null;
}

let appCuesInitTriggered = false;
let appCuesInitialized = false;
async function enableAppCues(appCuesScriptUrl) {
  return new Promise((resolve) => {
    // ensure the script is only runs once as otherwise AppCues might get loaded multiple times causing warn logs
    if (!appCuesInitTriggered) {
      appCuesInitTriggered = true;
      const appCuesScriptElement = document.createElement('script');
      appCuesScriptElement.src = appCuesScriptUrl;
      document.head.appendChild(appCuesScriptElement);

      appCuesScriptElement.onload = () => {
        appCuesInitialized = true;
        resolve(appCuesInitialized);
      };
    } else {
      resolve(appCuesInitialized);
    }
  });
}

export default withUser(Onboarding);
