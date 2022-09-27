/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCallback, useEffect, useState} from 'react';
import {useLocation, useNavigate} from 'react-router';
import {useBlocker} from './useBlocker';
import type {Location} from 'history';

export function useCallbackPrompt(when: boolean) {
  const navigate = useNavigate();
  const location = useLocation();
  const [showPrompt, setShowPrompt] = useState(false);
  const [lastLocation, setLastLocation] = useState<{location: Location} | null>(
    null
  );
  const [confirmedNavigation, setConfirmedNavigation] = useState(false);

  const cancelNavigation = useCallback(() => {
    setShowPrompt(false);
  }, []);

  const handleBlockedNavigation = useCallback(
    (nextLocation: {location: Location}) => {
      if (
        !confirmedNavigation &&
        nextLocation.location.pathname !== location.pathname
      ) {
        setShowPrompt(true);
        setLastLocation(nextLocation);
        return false;
      }
      return true;
    },
    [confirmedNavigation, location.pathname]
  );

  const confirmNavigation = useCallback(() => {
    setShowPrompt(false);
    setConfirmedNavigation(true);
  }, []);

  useEffect(() => {
    if (confirmedNavigation && lastLocation) {
      navigate(lastLocation.location);
    }
  }, [confirmedNavigation, lastLocation, navigate]);

  useEffect(() => {
    if (!when && confirmedNavigation) {
      setConfirmedNavigation(false);
    }
  }, [when, confirmedNavigation]);

  useBlocker(handleBlockedNavigation, when);

  return {showPrompt, confirmNavigation, cancelNavigation};
}
