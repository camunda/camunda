/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    (async () => {
      if (user) {
        const {orgId, clusterId, salesPlanType} = await getOnboardingConfig();
        window.Appcues?.identify(user.id, {
          roles: user.roles?.join('|'),
          orgId,
          clusters: clusterId,
          salesPlanType,
        });
      }
    })();
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
