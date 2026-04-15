/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InlineLoadingProps} from '@carbon/react';
import type {MutationStatus} from '@tanstack/react-query';
import {FitContentInlineLoading} from './styled';
import {useEffect} from 'react';

const LOADING_STATUS: Record<
  MutationStatus,
  NonNullable<InlineLoadingProps['status']>
> = {
  idle: 'inactive',
  pending: 'active',
  success: 'finished',
  error: 'error',
};

type AsyncActionTriggerProps = {
  status: MutationStatus;
  pendingLabel?: string;
  successLabel?: string;
  errorLabel?: string;
  resetDelay?: number;
  onReset?: () => void;
  children: React.ReactNode;
};

const AsyncActionTrigger: React.FC<AsyncActionTriggerProps> = (props) => {
  // `InlineLoading` is capable of calling reset after a timeout, but only for `success`...
  useEffect(() => {
    if (
      props.onReset &&
      (props.status === 'success' || props.status === 'error')
    ) {
      const timeoutId = setTimeout(props.onReset, props.resetDelay ?? 2000);
      return () => clearTimeout(timeoutId);
    }
    return undefined;
  }, [props.status, props.onReset, props.resetDelay]);

  if (props.status === 'idle') {
    return props.children;
  }

  const description: Record<typeof props.status, string> = {
    pending: props.pendingLabel ?? 'Pending...',
    success: props.successLabel ?? 'Successful!',
    error: props.errorLabel ?? 'Failed!',
  };

  return (
    <FitContentInlineLoading
      status={LOADING_STATUS[props.status]}
      description={description[props.status]}
    />
  );
};

export {type AsyncActionTriggerProps, AsyncActionTrigger};
