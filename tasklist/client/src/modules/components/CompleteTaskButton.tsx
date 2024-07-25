/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InlineLoadingStatus} from '@carbon/react';
import {AsyncActionButton} from './AsyncActionButton';
import {useTranslation} from 'react-i18next';

type Props = {
  submissionState: InlineLoadingStatus;
  onClick?: () => void;
  onSuccess?: () => void;
  onError?: () => void;
  isHidden: boolean;
  isDisabled: boolean;
};

function getCompletionButtonDescription(status: InlineLoadingStatus) {

  const {t} = useTranslation();

  if (status === 'active') {
    return t('completingTask');
  }

  if (status === 'error') {
    return t('completionFailed');
  }

  if (status === 'finished') {
    return t('completed');
  }

  return undefined;
}

const CompleteTaskButton: React.FC<Props> = ({
  submissionState,
  isHidden,
  isDisabled,
  onClick,
  onSuccess,
  onError,
}) => {

  const {t} = useTranslation();

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
          ? t('taskNotAssignedError')
          : undefined,
      }}
      status={submissionState}
      isHidden={isHidden}
      onError={onError}
    >
      {t('completeTask')}
    </AsyncActionButton>
  );
};

export {CompleteTaskButton};