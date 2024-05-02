/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {FormManager} from 'modules/formManager';
import {Variable} from 'modules/types';
import {InlineLoadingStatus} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {PoweredBy} from 'modules/components/PoweredBy';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {FormJSRenderer} from 'modules/components/FormJSRenderer';
import styles from './styles.module.scss';

const SUBMIT_OPERATION_MESSAGE = {
  active: 'Submitting...',
  error: 'Submission failed!',
  finished: 'Submitted!',
  inactive: undefined,
} as const;

type Props = {
  schema: string;
  onImportError: () => void;
  handleSubmit: (variables: Variable[]) => Promise<void>;
  onSubmitError: () => void;
  onSubmitSuccess: () => void;
};

const FormJS: React.FC<Props> = ({
  schema,
  handleSubmit,
  onImportError,
  onSubmitError,
  onSubmitSuccess,
}) => {
  const formManagerRef = useRef<FormManager | null>(null);
  const [isSchemaValid, setIsSchemaValid] = useState(true);
  const [submissionState, setSubmissionState] =
    useState<InlineLoadingStatus>('inactive');

  if (!isSchemaValid) {
    return null;
  }

  return (
    <div className={styles.container}>
      <div className={styles.formContainer}>
        {schema === null ? null : (
          <FormJSRenderer
            schema={schema}
            handleSubmit={handleSubmit}
            onMount={(formManager) => {
              formManagerRef.current = formManager;
            }}
            onImportError={() => {
              setIsSchemaValid(false);
              onImportError();
              tracking.track({
                eventName: 'public-start-form-invalid-form-schema',
              });
            }}
            onRender={() => {
              setIsSchemaValid(true);
            }}
            onSubmitStart={() => {
              setSubmissionState('active');
            }}
            onSubmitSuccess={() => {
              setSubmissionState('finished');
            }}
            onSubmitError={() => {
              setSubmissionState('error');
            }}
            onValidationError={() => {
              setSubmissionState('inactive');
            }}
          />
        )}
      </div>
      <div className={styles.submitButtonRow}>
        <AsyncActionButton
          inlineLoadingProps={{
            description: SUBMIT_OPERATION_MESSAGE[submissionState],
            'aria-live': ['error', 'finished'].includes(submissionState)
              ? 'assertive'
              : 'polite',
            onSuccess: () => {
              onSubmitSuccess();
              setSubmissionState('inactive');
            },
          }}
          buttonProps={{
            kind: 'primary',
            size: 'lg',
            type: 'submit',
            onClick: () => formManagerRef.current?.submit(),
          }}
          status={submissionState}
          onError={() => {
            onSubmitError();
            setSubmissionState('inactive');
          }}
        >
          Submit
        </AsyncActionButton>
        <PoweredBy />
      </div>
    </div>
  );
};

export {FormJS};
