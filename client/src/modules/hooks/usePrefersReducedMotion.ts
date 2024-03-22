/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
