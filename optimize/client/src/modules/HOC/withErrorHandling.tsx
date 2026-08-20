/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentType} from 'react';

import {useErrorHandling} from 'hooks';

export interface WithErrorHandlingProps<Error = unknown> {
  mightFail: <T = unknown, R = unknown, E extends Error = Error>(
    retriever: Promise<T>,
    successHandler: (response: T) => R,
    errorHandler?: ((error: E) => void) | undefined,
    finallyHandler?: () => void
  ) => Promise<R | undefined>;
  error?: Error;
  resetError?: () => void;
}

export default function withErrorHandling<P extends object, T = unknown>(
  Component: ComponentType<P>
): ComponentType<Omit<P, keyof WithErrorHandlingProps<T>>> {
  const Wrapper = (props: Omit<P, keyof WithErrorHandlingProps<T>>) => {
    const {error, mightFail, resetError} = useErrorHandling();

    return (
      <Component mightFail={mightFail<T>} error={error} resetError={resetError} {...(props as P)} />
    );
  };

  Wrapper.displayName = `${Component.displayName || Component.name || 'Component'}ErrorHandler`;

  Wrapper.WrappedComponent = Component;

  return Wrapper;
}
