/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCallback, useEffect, useRef} from 'react';

function useInterval(fn: () => void, ms: number = 0) {
  const interval = useRef<ReturnType<typeof setTimeout>>();
  const callback = useRef(fn);
  useEffect(() => {
    callback.current = fn;
  }, [fn]);

  const start = useCallback(() => {
    if (interval.current) {
      clearInterval(interval.current);
    }
    interval.current = setInterval(() => {
      callback.current();
    }, ms);
  }, [ms]);

  const stop = useCallback(() => {
    if (interval.current) {
      clearInterval(interval.current);
    }
  }, []);

  return [start, stop] as const;
}

export {useInterval};
