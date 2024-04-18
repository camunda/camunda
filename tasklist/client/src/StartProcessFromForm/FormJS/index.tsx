/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
