/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentType} from 'react';

import {useErrorHandling} from 'hooks';

export interface WithErrorHandlingProps<T = any> {
  mightFail: (
    retriever: Promise<T>,
    successHandler: ((response: any) => T) | undefined,
    errorHandler?: ((error: any) => void) | undefined,
    finallyHandler?: () => void
  ) => Promise<T | undefined>;
  error?: any;
  resetError?: () => void;
}

export default function withErrorHandling<P extends object, T = any>(
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
