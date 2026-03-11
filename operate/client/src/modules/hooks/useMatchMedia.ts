/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import {breakpoints} from '@carbon/elements';

// Copied from @carbon/react: https://github.com/carbon-design-system/carbon/blob/main/packages/react/src/internal/useMatchMedia.ts

/**
 * Listens to changes in a media query and returns whether it matches.
 * @param mediaQuery - The media query to listen to. For example, `(min-width: 600px)`.
 * @param defaultState - The initial state to return before the media query is evaluated. Defaults to `false`.
 * @returns Whether the media query matches.
 */
function useMatchMedia(mediaQuery: string, defaultState = false): boolean {
  const [matches, setMatches] = useState(defaultState);

  useEffect(() => {
    const listener = (event: MediaQueryListEvent) => {
      setMatches(event.matches);
    };
    const mediaQueryList = window.matchMedia(mediaQuery);
    mediaQueryList.addEventListener('change', listener, {passive: true});
    setMatches(mediaQueryList.matches);

    return () => {
      mediaQueryList.removeEventListener('change', listener);
    };
  }, [mediaQuery]);

  return matches;
}

// Inspired by @carbon/layout: https://github.com/carbon-design-system/carbon/blob/main/packages/layout/src/index.js

function isWidthAboveBreakpoint(name: keyof typeof breakpoints) {
  return `(min-width: ${breakpoints[name].width})`;
}

function isWidthBelowBreakpoint(name: keyof typeof breakpoints) {
  return `(max-width: ${breakpoints[name].width})`;
}

export {useMatchMedia, isWidthAboveBreakpoint, isWidthBelowBreakpoint};
