/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentType, useCallback, useRef} from 'react';
import {useState} from 'react';
import {useEffect} from 'react';

export interface WithErrorHandlingProps<T = any> {
  mightFail: (
    retriever: Promise<T>,
    cb: ((response: any) => T) | undefined,
    errorHandler?: ((error: any) => void) | undefined
  ) => Promise<T>;
  error?: any;
  resetError?: () => void;
}

export default function withErrorHandling<P extends object>(
  Component: ComponentType<P>
): ComponentType<Omit<P, keyof WithErrorHandlingProps>> {
  const Wrapper = (props: Omit<P, keyof WithErrorHandlingProps>) => {
    const [error, setError] = useState<any>(undefined);
    const mounted = useRef<boolean>();

    const mightFail = useCallback(
      async (
        retriever: Promise<any>,
        cb?: (response: any) => void,
        errorHandler?: (error: any) => void
      ) => {
        try {
          const response = await retriever;
          if (mounted.current) {
            return cb?.(response);
          }
        } catch (error) {
          if (mounted.current) {
            errorHandler?.(error);
            setError(error);
          }
        }
      },
      []
    );

    const resetError = useCallback(() => {
      setError(undefined);
    }, []);

    useEffect(() => {
      mounted.current = true;
      return () => {
        mounted.current = false;
      };
    }, []);

    return (
      <Component mightFail={mightFail} error={error} resetError={resetError} {...(props as P)} />
    );
  };

  Wrapper.displayName = `${Component.displayName || Component.name || 'Component'}ErrorHandler`;

  Wrapper.WrappedComponent = Component;

  return Wrapper;
}
