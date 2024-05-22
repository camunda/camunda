/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useState} from 'react';
import {useLocation, useNavigate} from 'react-router';
import {useBlocker} from './useBlocker';
import type {Location, Transition} from 'history';

/**
 * This effect interrupts the browser navigation whenever changes are detected in the URL.
 */
export function useCallbackPrompt({
  shouldInterrupt,
  onTransition,
}: {
  /**
   * Indicates if the navigation should be interrupted.
   */
  shouldInterrupt: boolean;
  /**
   * A callback function which hooks into the navigation interruption.
   *
   * - needs to return true if the navigation should be allowed (passed)
   * - needs to return false if the navigation should be interrupted
   */
  onTransition?: ({
    transition,
    location,
  }: {
    transition: Transition;
    location: Pick<Location, 'pathname' | 'search'>;
  }) => boolean;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const [isNavigationInterrupted, setIsNavigationInterrupted] = useState(false);
  const [lastLocation, setLastLocation] = useState<Location | null>(null);
  const [confirmedNavigation, setConfirmedNavigation] = useState(false);

  const cancelNavigation = useCallback(() => {
    setIsNavigationInterrupted(false);
  }, []);

  const handleBlockedNavigation = useCallback(
    (transition: Transition) => {
      const shouldPass = onTransition?.({
        transition,
        location: {pathname: location.pathname, search: location.search},
      });

      if (shouldPass === true) {
        setLastLocation(transition.location);
        transition.retry();
        return;
      }

      if (
        !confirmedNavigation &&
        (transition.location.pathname !== location.pathname ||
          transition.location.search !== location.search)
      ) {
        setIsNavigationInterrupted(true);
        setLastLocation(transition.location);
        return false;
      }
      return true;
    },
    [confirmedNavigation, location.pathname, location.search, onTransition],
  );

  const confirmNavigation = useCallback(() => {
    setIsNavigationInterrupted(false);
    setConfirmedNavigation(true);
  }, []);

  useEffect(() => {
    if (confirmedNavigation && lastLocation) {
      const contextPath = window.clientConfig?.baseName;

      if (contextPath !== undefined) {
        const pathname = lastLocation.pathname.replace(contextPath, '');

        navigate({
          ...lastLocation,
          pathname: pathname === '' ? '/' : pathname,
        });
      } else {
        navigate(lastLocation);
      }
    }
  }, [confirmedNavigation, lastLocation, navigate]);

  useEffect(() => {
    if (!shouldInterrupt && confirmedNavigation) {
      setConfirmedNavigation(false);
    }
  }, [shouldInterrupt, confirmedNavigation]);

  useBlocker(handleBlockedNavigation, shouldInterrupt);

  return {
    /**
     * Indicates if the browser navigation is currently interrupted
     */
    isNavigationInterrupted,
    /**
     * Continues the navigation in case it is currently interrupted
     */
    confirmNavigation,
    /**
     * Cancels the navigation in case it is currently interrupted
     */
    cancelNavigation,
  };
}
