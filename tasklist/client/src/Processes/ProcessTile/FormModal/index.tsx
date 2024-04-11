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

import {FormManager} from 'modules/formManager';
import {useForm} from 'modules/queries/useForm';
import {Process, Variable} from 'modules/types';
import {getProcessDisplayName} from 'modules/utils/getProcessDisplayName';
import {useRef, useState} from 'react';
import {
  FormCenterContainer,
  FormContainer,
  FormScrollContainer,
  FormSkeletonContainer,
  InlineErrorContainer,
  InlineNotification,
} from './styled';
import {TextInputSkeleton, Loading, Modal, Copy, Layer} from '@carbon/react';
import {Share} from '@carbon/react/icons';
import {match} from 'ts-pattern';
import {FormJSRenderer} from 'modules/components/FormJSRenderer';
import {createPortal} from 'react-dom';

type Props = {
  process: Process;
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (variables: Variable[]) => Promise<void>;
  isMultiTenancyEnabled: boolean;
  tenantId?: string;
};

const FormModal: React.FC<Props> = ({
  isOpen,
  onClose,
  process,
  onSubmit,
  isMultiTenancyEnabled,
  tenantId,
}) => {
  const formManagerRef = useRef<FormManager | null>(null);
  const processDisplayName = getProcessDisplayName(process);
  const {data, fetchStatus, status} = useForm(
    {
      id: process.startEventFormId!,
      processDefinitionKey: process.id,
      version: 'latest',
    },
    {
      enabled: isOpen && process.startEventFormId !== null,
      refetchOnReconnect: false,
      refetchOnWindowFocus: false,
    },
  );
  const [isFormSchemaValid, setIsFormSchemaValid] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [hasSubmissionFailed, setHasSubmissionFailed] = useState(false);
  const {schema} = data ?? {};
  const prioritizeTenantValidation =
    isMultiTenancyEnabled && tenantId === undefined;

  const handleClose = () => {
    onClose();
    setIsSubmitting(false);
    setHasSubmissionFailed(false);
    setIsFormSchemaValid(true);
  };

  return createPortal(
    <Layer level={0}>
      <Modal
        aria-label={`Start process ${processDisplayName}`}
        modalHeading={
          <>
            {`Start process ${processDisplayName}`}
            <Copy
              feedback="Copied"
              onClick={() => {
                navigator.clipboard.writeText(window.location.href);
              }}
              align="bottom"
              className="cds--copy-btn"
              aria-label="Share process URL"
            >
              <Share />
            </Copy>
          </>
        }
        secondaryButtonText="Cancel"
        primaryButtonText={
          <>
            Start process
            {isSubmitting ? (
              <Loading
                withOverlay={false}
                small
                data-testid="loading-spinner"
              />
            ) : null}
          </>
        }
        open={isOpen}
        onRequestClose={handleClose}
        onRequestSubmit={() => {
          formManagerRef.current?.submit();
        }}
        onSecondarySubmit={handleClose}
        preventCloseOnClickOutside
        primaryButtonDisabled={
          status !== 'success' || !isFormSchemaValid || isSubmitting
        }
        size="lg"
      >
        <FormContainer>
          {match({
            status,
            fetchStatus,
            isFormSchemaValid,
          })
            .with({fetchStatus: 'fetching'}, () => (
              <FormSkeletonContainer data-testid="form-skeleton">
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
              </FormSkeletonContainer>
            ))
            .with(
              {
                status: 'success',
                isFormSchemaValid: true,
              },
              () => (
                <>
                  <FormScrollContainer>
                    <FormCenterContainer>
                      <FormJSRenderer
                        schema={schema!}
                        handleSubmit={onSubmit}
                        onMount={(formManager) => {
                          formManagerRef.current = formManager;
                        }}
                        onSubmitStart={() => {
                          setIsSubmitting(true);
                        }}
                        onImportError={() => {
                          setIsFormSchemaValid(false);
                        }}
                        onSubmitError={() => {
                          setHasSubmissionFailed(true);
                          setIsSubmitting(false);
                        }}
                        onSubmitSuccess={() => {
                          setIsSubmitting(false);
                        }}
                        onValidationError={() => {
                          setIsSubmitting(false);
                        }}
                      />
                    </FormCenterContainer>
                  </FormScrollContainer>
                  <InlineErrorContainer>
                    {match({
                      hasSubmissionFailed,
                      prioritizeTenantValidation,
                    })
                      .with(
                        {
                          hasSubmissionFailed: true,
                          prioritizeTenantValidation: true,
                        },
                        () => (
                          <InlineNotification
                            kind="error"
                            role="alert"
                            hideCloseButton
                            lowContrast
                            title="Something went wrong"
                            subtitle="You must first select a tenant to start a process."
                          />
                        ),
                      )
                      .with(
                        {
                          hasSubmissionFailed: true,
                          prioritizeTenantValidation: false,
                        },
                        () => (
                          <InlineNotification
                            kind="error"
                            role="alert"
                            hideCloseButton
                            lowContrast
                            title="Something went wrong"
                            subtitle="Form could not be submitted. Please try again later."
                          />
                        ),
                      )
                      .with(
                        {
                          hasSubmissionFailed: false,
                        },
                        () => null,
                      )
                      .exhaustive()}
                  </InlineErrorContainer>
                </>
              ),
            )
            .with({status: 'success', isFormSchemaValid: false}, () => (
              <InlineNotification
                kind="error"
                role="alert"
                hideCloseButton
                lowContrast
                title="Something went wrong"
                subtitle="We were not able to render the form. Please contact your process administrator to fix the form schema."
              />
            ))
            .with({status: 'error'}, () => (
              <InlineNotification
                kind="error"
                role="alert"
                hideCloseButton
                lowContrast
                title="Something went wrong"
                subtitle="We were not able to load the form. Please try again or contact your Tasklist administrator."
              />
            ))
            .exhaustive()}
        </FormContainer>
      </Modal>
    </Layer>,
    document.body,
  );
};

export {FormModal};
