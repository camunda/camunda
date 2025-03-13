/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FormManager} from 'common/form-js/formManager';
import {useForm} from 'v1/api/useForm.query';
import type {Process} from 'v1/api/types';
import {getProcessDisplayName} from 'v1/utils/getProcessDisplayName';
import {useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
  TextInputSkeleton,
  Loading,
  Modal,
  Copy,
  InlineNotification,
  Layer,
} from '@carbon/react';
import {Share} from '@carbon/react/icons';
import {match} from 'ts-pattern';
import {FormJSRenderer} from 'common/form-js/FormJSRenderer';
import {createPortal} from 'react-dom';
import styles from './styles.module.scss';

type Props = {
  process: Process;
  isOpen: boolean;
  onClose: () => void;
  onSubmit: React.ComponentProps<typeof FormJSRenderer>['handleSubmit'];
  onFileUpload: React.ComponentProps<typeof FormJSRenderer>['handleFileUpload'];
  isMultiTenancyEnabled: boolean;
  tenantId?: string;
};

const FormModal: React.FC<Props> = ({
  isOpen,
  onClose,
  process,
  onSubmit,
  onFileUpload,
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
  const {t} = useTranslation();
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
        aria-label={t('processesStartProcessWithForm', {processDisplayName})}
        modalHeading={
          <>
            {t('processesStartProcessWithForm', {processDisplayName})}
            <Copy
              feedback={t('processesStartProcessWithFormCopyURLButtonLabel')}
              onClick={() => {
                navigator.clipboard.writeText(window.location.href);
              }}
              align="bottom"
              className="cds--copy-btn"
              aria-label={t('processesStartProcessWithFormShareURLAriaLabel')}
            >
              <Share />
            </Copy>
          </>
        }
        secondaryButtonText={t('processesProcessTileCancelButtonLabel')}
        primaryButtonText={
          <>
            {t('processesStartProcessWithFormStartButtonLabel')}
            {isSubmitting ? (
              <Loading
                withOverlay={false}
                small
                data-testid="loading-spinner"
              />
            ) : null}
          </>
        }
        closeButtonLabel={t('optionsModalCloseButton')}
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
        <div className={styles.formContainer}>
          {match({
            status,
            fetchStatus,
            isFormSchemaValid,
          })
            .with({fetchStatus: 'fetching'}, () => (
              <div
                className={styles.formSkeletonContainer}
                data-testid="form-skeleton"
              >
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
                <TextInputSkeleton />
              </div>
            ))
            .with(
              {
                status: 'success',
                isFormSchemaValid: true,
              },
              () => (
                <>
                  <div className={styles.formScrollContainer}>
                    <div className={styles.formCenterContainer}>
                      <FormJSRenderer
                        schema={schema!}
                        handleSubmit={onSubmit}
                        handleFileUpload={onFileUpload}
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
                    </div>
                  </div>
                  <div className={styles.inlineErrorContainer}>
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
                            title={t('errorGenericErrorTitle')}
                            subtitle={t('processesFetchErrorMissingTenant')}
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
                            className={styles.inlineNotification}
                            kind="error"
                            role="alert"
                            hideCloseButton
                            lowContrast
                            title={t('errorGenericErrorTitle')}
                            subtitle={t(
                              'processesStartProcessWithModalSubmissionFailed',
                            )}
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
                  </div>
                </>
              ),
            )
            .with({status: 'success', isFormSchemaValid: false}, () => (
              <InlineNotification
                kind="error"
                role="alert"
                hideCloseButton
                lowContrast
                title={t('errorGenericErrorTitle')}
                subtitle={t(
                  'processesStartProcessWithModalFormRenderingFailed',
                )}
              />
            ))
            .with({status: 'error'}, () => (
              <InlineNotification
                kind="error"
                role="alert"
                hideCloseButton
                lowContrast
                title={t('errorGenericErrorTitle')}
                subtitle={t('processesStartProcessWithModalFormLoadFailed')}
              />
            ))
            .exhaustive()}
        </div>
      </Modal>
    </Layer>,
    document.body,
  );
};

export {FormModal};
