/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';

export default function useErrorHandling<Error = unknown>() {
  const [error, setError] = useState<Error | undefined>(undefined);
  const mounted = useRef<boolean>();

  const mightFail = useCallback(async function callback<
    T = unknown,
    R = unknown,
    E extends Error = Error,
  >(
    retriever: Promise<T>,
    successHandler: (response: T) => R,
    errorHandler?: ((error: E) => void) | undefined,
    finallyHandler?: () => void
  ): Promise<R | undefined> {
    try {
      const response = await retriever;
      if (mounted.current) {
        return await successHandler(response);
      }
    } catch (error) {
      if (mounted.current) {
        errorHandler?.(error as E);
        setError(error as E);
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
