/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef, useState} from 'react';
import {Container, FormContainer, SubmitButtonRow} from './styled';
import {FormManager} from 'modules/formManager';
import {Variable} from 'modules/types';
import {InlineLoadingStatus} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {PoweredBy} from 'modules/components/PoweredBy';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {FormJSRenderer} from 'modules/components/FormJSRenderer';

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
    <Container>
      <FormContainer>
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
      </FormContainer>
      <SubmitButtonRow>
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
      </SubmitButtonRow>
    </Container>
  );
};

export {FormJS};
