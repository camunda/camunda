/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InlineLoadingStatus} from '@carbon/react';
import {AsyncActionButton} from './AsyncActionButton';

type Props = {
  submissionState: InlineLoadingStatus;
  onClick?: () => void;
  onSuccess?: () => void;
  onError?: () => void;
  isHidden: boolean;
  isDisabled: boolean;
};

function getCompletionButtonDescription(status: InlineLoadingStatus) {
  if (status === 'active') {
    return 'Completing task...';
  }

  if (status === 'error') {
    return 'Completion failed';
  }

  if (status === 'finished') {
    return 'Completed';
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
          ? undefined
          : 'You must first assign this task to complete it',
      }}
      status={submissionState}
      isHidden={isHidden}
      onError={onError}
    >
      Complete Task
    </AsyncActionButton>
  );
};

export {CompleteTaskButton};
