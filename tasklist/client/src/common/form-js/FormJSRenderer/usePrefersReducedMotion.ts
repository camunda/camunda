/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';

const QUERY = '(prefers-reduced-motion: no-preference)';

function usePrefersReducedMotion() {
  const [value, setValue] = useState(() => !window.matchMedia(QUERY).matches);
  useEffect(() => {
    const mediaQueryList = window.matchMedia(QUERY);
    function listener(event: MediaQueryListEvent) {
      setValue(!event.matches);
    }
    mediaQueryList.addEventListener('change', listener);
    return () => {
      mediaQueryList.removeEventListener('change', listener);
    };
  }, []);
  return value;
}

export {usePrefersReducedMotion};
