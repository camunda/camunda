/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
