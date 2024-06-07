/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect, ComponentProps} from 'react';

import {getOptimizeProfile} from 'config';

import PlatformLogin from './PlatformLogin';

export function Login(props: ComponentProps<typeof PlatformLogin>) {
  const [optimizeProfile, setOptimizeProfile] = useState<string>();

  useEffect(() => {
    (async () => {
      const profile = await getOptimizeProfile();
      setOptimizeProfile(profile);
      // We need to reload in C8 to reinitialise the authentication flow
      if (profile !== 'platform') {
        window.location.reload();
      }
    })();
  }, []);

  return optimizeProfile === 'platform' ? <PlatformLogin {...props} /> : null;
}

export default Login;
