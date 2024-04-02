/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';

function useIsPageBlurred() {
  const [tabBlur, setTabBlur] = useState(document.visibilityState === 'hidden');
  const [windowBlur, setWindowBlur] = useState(false);

  useEffect(() => {
    function onVisibilityStateChange() {
      setTabBlur(document.visibilityState === 'hidden');
    }
    function onWindowBlur() {
      setWindowBlur(true);
    }
    function onWindowFocus() {
      setWindowBlur(false);
    }

    document.addEventListener('visibilitychange', onVisibilityStateChange);
    window.addEventListener('blur', onWindowBlur);
    window.addEventListener('focus', onWindowFocus);

    return () => {
      document.removeEventListener('visibilitychange', onVisibilityStateChange);
      window.removeEventListener('blur', onWindowBlur);
      window.removeEventListener('focus', onWindowFocus);
    };
  }, []);

  return windowBlur || tabBlur;
}

export {useIsPageBlurred};
