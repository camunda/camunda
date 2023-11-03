/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FormManager} from 'modules/formManager';
import {useForm} from 'modules/queries/useForm';
import {Process, Variable} from 'modules/types';
import {getProcessDisplayName} from 'modules/utils/getProcessDisplayName';
import {useRef, useState} from 'react';
import {
  FormContainer,
  FormSkeletonContainer,
  InlineNotification,
} from './styled';
import {TextInputSkeleton, Loading, Modal} from '@carbon/react';
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

  return createPortal(
    <>
      <Modal
        aria-label={`Start process ${processDisplayName}`}
        modalHeading={`Start process ${processDisplayName}`}
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
        onRequestClose={onClose}
        onRequestSubmit={() => {
          formManagerRef.current?.submit();
        }}
        onSecondarySubmit={onClose}
        preventCloseOnClickOutside
        primaryButtonDisabled={
          status !== 'success' || !isFormSchemaValid || isSubmitting
        }
        size="lg"
      >
        {match({
          status,
          fetchStatus,
          isFormSchemaValid,
        })
          .with({fetchStatus: 'fetching'}, () => (
            <FormContainer>
              <FormSkeletonContainer data-testid="form-skeleton">
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
              </FormSkeletonContainer>
            </FormContainer>
          ))
          .with(
            {
              status: 'success',
              isFormSchemaValid: true,
            },
            () => (
              <FormContainer>
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
              </FormContainer>
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
              subtitle="We were not able to load the form. Please check your connection
              and try again later."
            />
          ))
          .exhaustive()}
      </Modal>
    </>,
    document.body,
  );
};

export {FormModal};
