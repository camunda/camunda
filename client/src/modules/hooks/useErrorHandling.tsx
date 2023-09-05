/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCallback, useEffect, useRef, useState} from 'react';

export default function useErrorHandling() {
  const [error, setError] = useState<any>(undefined);
  const mounted = useRef<boolean>();

  const mightFail = useCallback(async function callback<T>(
    retriever: Promise<T>,
    successHandler: ((response: T) => Promise<T | void> | T | void) | undefined,
    errorHandler?: ((error: any) => void) | undefined,
    finallyHandler?: () => void
  ) {
    try {
      const response = await retriever;
      if (mounted.current) {
        return successHandler?.(response);
      }
    } catch (error) {
      if (mounted.current) {
        errorHandler?.(error);
        setError(error);
      }
    } finally {
      finallyHandler?.();
    }
  }, []);

  const resetError = useCallback(() => {
    setError(undefined);
  }, []);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  return {error, resetError, mightFail};
}
