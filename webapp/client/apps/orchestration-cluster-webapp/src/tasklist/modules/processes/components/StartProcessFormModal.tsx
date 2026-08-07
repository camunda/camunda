/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useRef, useState, type ReactNode} from 'react';
import {
	Button,
	ComposedModal,
	InlineNotification,
	ModalBody,
	ModalFooter,
	ModalHeader,
	TextInputSkeleton,
} from '@carbon/react';
import {Share} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.10';
import {CamundaFormRenderer, type PartialVariable} from '#/tasklist/modules/form-js/CamundaFormRenderer';
import type {FormManager} from '#/tasklist/modules/form-js/FormManager';
import {ProcessStartFormImportError} from '#/shared/errors';
import styles from './StartProcessFormModal.module.scss';

type CommonProps = {
	processDisplayName: string;
	onClose: () => void;
};

type Props = CommonProps & {
	schema: string;
	isMultiTenancyEnabled: boolean;
	tenantId: string | undefined;
	onSubmit: (variables: PartialVariable[]) => Promise<void>;
	onFileUpload: (files: Map<string, File[]>) => Promise<Map<string, DocumentReference[]>>;
};

type ErrorVariant = 'not-found' | 'forbidden' | 'load-failed' | 'schema-import-failed';

type ErrorProps = CommonProps & {
	variant: ErrorVariant;
	onRetry?: () => void;
};

type ShellProps = CommonProps & {
	children: ReactNode;
	footer: ReactNode;
	isShareButtonVisible?: boolean;
};

function getErrorSubtitleKey(variant: ErrorVariant) {
	if (variant === 'not-found') {
		return 'tasklist.processesProcessNoFormOrNotExistError';
	}

	if (variant === 'schema-import-failed') {
		return 'tasklist.processesStartProcessWithModalFormRenderingFailed';
	}

	if (variant === 'forbidden') {
		return 'tasklist.taskActionForbidden';
	}

	return 'tasklist.processesStartProcessWithModalFormLoadFailed';
}

const StartProcessFormModalShell: React.FC<ShellProps> = ({
	processDisplayName,
	onClose,
	children,
	footer,
	isShareButtonVisible = false,
}) => {
	const {t} = useTranslation();
	const title = t('tasklist.processesStartProcessWithForm', {processDisplayName});
	const handleShareButtonClick = useCallback(async () => {
		try {
			await navigator.clipboard.writeText(window.location.href);
		} catch (error) {
			console.error('Failed to copy URL to clipboard', error);
		}
	}, []);

	return (
		<ComposedModal open preventCloseOnClickOutside size="lg" onClose={onClose} aria-label={title}>
			<ModalHeader
				title={
					<div className={styles.title}>
						<span>{title}</span>
						{isShareButtonVisible ? (
							<Button
								kind="ghost"
								size="sm"
								hasIconOnly
								renderIcon={Share}
								iconDescription={t('tasklist.processesStartProcessWithFormShareURLAriaLabel')}
								tooltipPosition="right"
								onClick={handleShareButtonClick}
								aria-label={t('tasklist.processesStartProcessWithFormShareURLAriaLabel')}
							/>
						) : null}
					</div>
				}
				iconDescription={t('tasklist.optionsModalCloseButton')}
			/>
			<ModalBody hasScrollingContent>
				<div className={styles.formContainer}>{children}</div>
			</ModalBody>
			{footer}
		</ComposedModal>
	);
};

const StartProcessFormModal: React.FC<Props> = ({
	processDisplayName,
	schema,
	isMultiTenancyEnabled,
	tenantId,
	onClose,
	onSubmit,
	onFileUpload,
}) => {
	const {t} = useTranslation();
	const formManagerRef = useRef<FormManager | null>(null);
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [hasSubmissionFailed, setHasSubmissionFailed] = useState(false);
	const [formImportFailure, setFormImportFailure] = useState<{cause: unknown} | null>(null);

	if (formImportFailure !== null) {
		throw new ProcessStartFormImportError(formImportFailure.cause);
	}

	return (
		<StartProcessFormModalShell
			processDisplayName={processDisplayName}
			onClose={onClose}
			isShareButtonVisible
			footer={
				<ModalFooter
					primaryButtonText={t('tasklist.processesStartProcessWithFormStartButtonLabel')}
					primaryButtonDisabled={isSubmitting}
					secondaryButtonText={t('tasklist.processesProcessTileCancelButtonLabel')}
					onRequestSubmit={() => formManagerRef.current?.submit()}
					loadingStatus={isSubmitting ? 'active' : 'inactive'}
					loadingDescription={t('tasklist.processesStartProcessPendingStatusText')}
				>
					{null}
				</ModalFooter>
			}
		>
			<CamundaFormRenderer
				schema={schema}
				layerLevel={1}
				handleSubmit={onSubmit}
				handleFileUpload={onFileUpload}
				onMount={(formManager) => {
					formManagerRef.current = formManager;
				}}
				onSubmitStart={() => {
					setIsSubmitting(true);
					setHasSubmissionFailed(false);
				}}
				onImportError={(cause) => {
					setFormImportFailure({cause});
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
			{hasSubmissionFailed ? (
				<div className={styles.inlineErrorContainer}>
					<InlineNotification
						className={styles.inlineNotification}
						kind="error"
						role="alert"
						hideCloseButton
						lowContrast
						title={t('errorGenericErrorPageTitle')}
						subtitle={
							isMultiTenancyEnabled && tenantId === undefined
								? t('tasklist.processesFetchErrorMissingTenant')
								: t('tasklist.processesStartProcessWithModalSubmissionFailed')
						}
					/>
				</div>
			) : null}
		</StartProcessFormModalShell>
	);
};

const StartProcessFormModalSkeleton: React.FC<CommonProps> = ({processDisplayName, onClose}) => {
	const {t} = useTranslation();

	return (
		<StartProcessFormModalShell
			processDisplayName={processDisplayName}
			onClose={onClose}
			footer={
				<ModalFooter
					primaryButtonText={t('tasklist.processesStartProcessWithFormStartButtonLabel')}
					primaryButtonDisabled
					secondaryButtonText={t('tasklist.processesProcessTileCancelButtonLabel')}
				>
					{null}
				</ModalFooter>
			}
		>
			<div className={styles.formSkeletonContainer} data-testid="form-skeleton">
				{Array.from({length: 6}, (_, index) => (
					<TextInputSkeleton key={index} />
				))}
			</div>
		</StartProcessFormModalShell>
	);
};

const StartProcessFormModalError: React.FC<ErrorProps> = ({processDisplayName, variant, onClose, onRetry}) => {
	const {t} = useTranslation();
	const isForbidden = variant === 'forbidden';
	const subtitleKey = getErrorSubtitleKey(variant);

	return (
		<StartProcessFormModalShell
			processDisplayName={processDisplayName}
			onClose={onClose}
			footer={
				<ModalFooter>
					<Button kind="secondary" onClick={onClose}>
						{t('tasklist.processesProcessTileCancelButtonLabel')}
					</Button>
					{onRetry === undefined ? null : <Button onClick={onRetry}>{t('errorGenericErrorPageButtonLabel')}</Button>}
				</ModalFooter>
			}
		>
			<InlineNotification
				kind="error"
				role="alert"
				hideCloseButton
				lowContrast
				title={t(isForbidden ? 'tasklist.taskDetailsProcessForbiddenTitle' : 'errorGenericErrorPageTitle')}
				subtitle={t(subtitleKey, {processDisplayName})}
			/>
		</StartProcessFormModalShell>
	);
};

export {StartProcessFormModal, StartProcessFormModalError, StartProcessFormModalSkeleton};
export type {ErrorVariant as StartProcessFormModalErrorVariant};
