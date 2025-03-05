/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type InlineLoadingProps} from '@carbon/react';
import {AsyncActionButton} from 'common/components/AsyncActionButton';
import {t} from 'i18next';
import {getCompletionButtonDescription} from './getCompletionButtonDescription';

type Props = {
  submissionState: NonNullable<InlineLoadingProps['status']>;
  onClick?: () => void;
  onSuccess?: () => void;
  onError?: () => void;
  isHidden: boolean;
  isDisabled: boolean;
};

const CompleteTaskButton: React.FC<Props> = ({
  submissionState,
  isHidden,
  isDisabled,
  onClick,
  onSuccess,
  onError,
}) => {
  return (
    <AsyncActionButton
      inlineLoadingProps={{
        description: getCompletionButtonDescription(submissionState),
        'aria-live': 'polite',
        onSuccess,
      }}
      buttonProps={{
        size: 'md',
        type: 'submit',
        disabled: submissionState === 'active' || isDisabled,
        onClick,
        title: isDisabled
          ? t('taskDetailsDisabledCompleteButtonTitle')
          : undefined,
      }}
      status={submissionState}
      isHidden={isHidden}
      onError={onError}
    >
      {t('taskDetailsCompleteTaskButtonLabel')}
    </AsyncActionButton>
  );
};

export {CompleteTaskButton};
