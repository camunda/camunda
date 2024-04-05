/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  ButtonDefaultProps,
  ButtonKindProps,
  InlineLoading as OriginalInlineLoading,
} from '@carbon/react';
import {useEffect} from 'react';
import {Button, InlineLoading} from './styled';

type Props = {
  inlineLoadingProps?: Omit<
    React.ComponentProps<typeof OriginalInlineLoading>,
    'status' | 'successDelay'
  >;
  buttonProps?: ButtonDefaultProps & ButtonKindProps;
  children?: React.ReactNode;
  status: React.ComponentProps<typeof OriginalInlineLoading>['status'];
  onError?: () => void;
};

const AsyncActionButton: React.FC<Props> = ({
  children,
  inlineLoadingProps,
  buttonProps,
  status,
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
    <Button {...buttonProps}>{children}</Button>
  ) : (
    <InlineLoading {...restInlineLoadingProps} status={status} />
  );
};

export {AsyncActionButton};
