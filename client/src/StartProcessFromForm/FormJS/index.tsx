/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {
  Container,
  FormContainer,
  FormCustomStyling,
  FormRoot,
  SubmitButtonRow,
} from './styled';
import {formManager} from 'modules/formManager';
import {Variable} from 'modules/types';
import {InlineLoadingStatus, Layer} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {PoweredBy} from 'modules/components/PoweredBy';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';

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
  const formContainerRef = useRef<HTMLDivElement | null>(null);
  const [isSchemaValid, setIsSchemaValid] = useState(true);
  const [submissionState, setSubmissionState] =
    useState<InlineLoadingStatus>('inactive');

  useEffect(() => {
    const container = formContainerRef.current;

    if (container !== null) {
      setIsSchemaValid(true);
      formManager.render({
        container,
        schema,
        data: {},
        onImportError: () => {
          setIsSchemaValid(false);
          onImportError();
          tracking.track({
            eventName: 'public-start-form-invalid-form-schema',
          });
        },
        onSubmit: async ({data, errors}) => {
          setSubmissionState('active');
          if (Object.keys(errors).length === 0) {
            const variables = Object.entries(data).map(
              ([name, value]) =>
                ({
                  name,
                  value: JSON.stringify(value),
                }) as Variable,
            );

            try {
              await handleSubmit(variables);
              setSubmissionState('finished');
            } catch {
              setSubmissionState('error');
            }
          } else {
            setSubmissionState('error');
          }
        },
      });
    }

    return () => {};
  }, [schema, handleSubmit, onImportError]);

  useEffect(() => {
    return () => {
      formManager.detach();
    };
  }, []);

  if (!isSchemaValid) {
    return null;
  }

  return (
    <Container>
      <FormCustomStyling />
      <FormContainer>
        <Layer>
          <FormRoot ref={formContainerRef} />
        </Layer>
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
            onClick: () => formManager.submit(),
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
