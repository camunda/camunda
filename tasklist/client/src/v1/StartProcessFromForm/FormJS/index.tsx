/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {FormManager} from 'common/form-js/formManager';
import {type InlineLoadingProps, Layer} from '@carbon/react';
import {tracking} from 'common/tracking';
import {PoweredBy} from 'common/components/PoweredBy';
import {AsyncActionButton} from 'common/components/AsyncActionButton';
import {FormJSRenderer} from 'common/form-js/FormJSRenderer';
import styles from './styles.module.scss';
import type {PartialVariable} from 'common/types';

const SUBMIT_OPERATION_MESSAGE = {
  active: 'Submitting...',
  error: 'Submission failed!',
  finished: 'Submitted!',
  inactive: undefined,
} as const;

type Props = {
  schema: string;
  onImportError: () => void;
  handleSubmit: (variables: PartialVariable[]) => Promise<void>;
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
  const {t} = useTranslation();
  const [submissionState, setSubmissionState] =
    useState<NonNullable<InlineLoadingProps['status']>>('inactive');

  if (!isSchemaValid) {
    return null;
  }

  return (
    <div className={styles.container}>
      <div className={styles.formContainer}>
        {schema === null ? null : (
          <Layer>
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
          </Layer>
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
          {t('processesStartProcessWithFormSubmitButtonLabel')}
        </AsyncActionButton>
        <PoweredBy />
      </div>
    </div>
  );
};

export {FormJS};
