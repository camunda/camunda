/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {useBlocker, type Location} from 'react-router-dom';

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
    currentLocation,
    nextLocation,
    historyAction,
  }: {
    currentLocation: Location;
    nextLocation: Location;
    historyAction: string;
  }) => boolean;
}) {
  const shouldBlock = useCallback(
    ({
      currentLocation,
      nextLocation,
      historyAction,
    }: {
      currentLocation: Location;
      nextLocation: Location;
      historyAction: string;
    }) => {
      if (!shouldInterrupt) {
        return false;
      }

      const shouldPass = onTransition?.({
        currentLocation,
        nextLocation,
        historyAction,
      });

      if (shouldPass === true) {
        return false;
      }

      return (
        nextLocation.pathname !== currentLocation.pathname ||
        nextLocation.search !== currentLocation.search
      );
    },
    [shouldInterrupt, onTransition],
  );

  const blocker = useBlocker(shouldBlock);

  const confirmNavigation = useCallback(() => {
    if (blocker.state === 'blocked') {
      blocker.proceed();
    }
  }, [blocker]);

  const cancelNavigation = useCallback(() => {
    if (blocker.state === 'blocked') {
      blocker.reset();
    }
  }, [blocker]);

  return {
    /**
     * Indicates if the browser navigation is currently interrupted
     */
    isNavigationInterrupted: blocker.state === 'blocked',
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
