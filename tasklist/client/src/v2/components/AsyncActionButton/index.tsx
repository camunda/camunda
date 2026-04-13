/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type InlineLoadingProps, Button, InlineLoading} from '@carbon/react';
import React, {useEffect} from 'react';
import styles from './styles.module.scss';
import cn from 'classnames';

type Props = {
  inlineLoadingProps?: Omit<InlineLoadingProps, 'status' | 'successDelay'>;
  buttonProps?: React.ComponentProps<typeof Button>;
  children?: React.ReactNode;
  status: NonNullable<InlineLoadingProps['status']>;
  isHidden?: boolean;
  onError?: () => void;
};

const AsyncActionButton: React.FC<Props> = ({
  children,
  inlineLoadingProps,
  buttonProps,
  status,
  isHidden,
  onError,
}) => {
  const {onSuccess, ...restInlineLoadingProps} = inlineLoadingProps ?? {};

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout>;

    if (onError !== undefined && status === 'error') {
      timeoutId = setTimeout(onError, 500);
    }

    return () => {
      clearTimeout(timeoutId);
    };
  }, [onError, status]);

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout>;

    if (onSuccess !== undefined && status === 'finished') {
      timeoutId = setTimeout(onSuccess, 500);
    }

    return () => {
      clearTimeout(timeoutId);
    };
  }, [onSuccess, status]);

  return status === 'inactive' ? (
    <Button
      {...buttonProps}
      className={cn({hide: isHidden}, buttonProps?.className, styles.button)}
    >
      {children}
    </Button>
  ) : (
    <InlineLoading
      {...restInlineLoadingProps}
      className={cn(restInlineLoadingProps.className, styles.fitContent)}
      status={status}
    />
  );
};

export {AsyncActionButton};
