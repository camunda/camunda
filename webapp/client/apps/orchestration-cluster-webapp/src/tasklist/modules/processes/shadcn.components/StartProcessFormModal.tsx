/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {X} from 'lucide-react';
import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.10';
import {
	Alert,
	Button,
	Dialog,
	DialogBody,
	DialogClose,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Skeleton,
} from '@camunda/design-system';
import {CamundaFormRenderer, type PartialVariable} from '#/tasklist/modules/form-js/CamundaFormRenderer';
import type {FormManager} from '#/tasklist/modules/form-js/FormManager';
import {ProcessStartFormImportError} from '#/shared/errors';
import {cn} from '#/shared/cn';
import {CopyLinkButton} from './CopyLinkButton';

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
	children: React.ReactNode;
	footer: React.ReactNode;
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

const StartProcessFormModalShell: React.FC<ShellProps> = ({processDisplayName, onClose, children, footer}) => {
	const {t} = useTranslation();

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent
				size="lg"
				showCloseButton={false}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				<DialogHeader>
					<DialogTitle>{t('tasklist.processesStartProcessWithForm', {processDisplayName})}</DialogTitle>
				</DialogHeader>
				<DialogBody>
					<div className="mx-auto w-full max-w-[900px]">{children}</div>
				</DialogBody>
				{footer}
				<DialogClose asChild>
					<Button
						type="button"
						variant="ghost"
						size="icon-sm"
						className="absolute top-2 right-2"
						aria-label={t('tasklist.optionsModalCloseButton')}
					>
						<X aria-hidden />
					</Button>
				</DialogClose>
			</DialogContent>
		</Dialog>
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
			footer={
				<DialogFooter>
					<CopyLinkButton textToCopy={window.location.href} />
					<Button type="button" variant="secondary" disabled={isSubmitting} onClick={onClose}>
						{t('tasklist.processesProcessTileCancelButtonLabel')}
					</Button>
					<Button type="button" loading={isSubmitting} onClick={() => formManagerRef.current?.submit()}>
						{isSubmitting
							? t('tasklist.processesStartProcessPendingStatusText')
							: t('tasklist.processesStartProcessWithFormStartButtonLabel')}
					</Button>
				</DialogFooter>
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
				<Alert
					className="mt-2"
					variant="destructive"
					title={t('errorGenericErrorPageTitle')}
					description={
						isMultiTenancyEnabled && tenantId === undefined
							? t('tasklist.processesFetchErrorMissingTenant')
							: t('tasklist.processesStartProcessWithModalSubmissionFailed')
					}
				/>
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
				<DialogFooter>
					<Button type="button" variant="secondary" onClick={onClose}>
						{t('tasklist.processesProcessTileCancelButtonLabel')}
					</Button>
					<Button type="button" disabled>
						{t('tasklist.processesStartProcessWithFormStartButtonLabel')}
					</Button>
				</DialogFooter>
			}
		>
			<div className="grid grid-cols-1 gap-4 sm:grid-cols-2" data-testid="form-skeleton">
				{Array.from({length: 6}, (_, index) => (
					<Skeleton key={index} className={cn('h-9 w-full', index === 2 && 'sm:col-span-2')} />
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
				<DialogFooter>
					<Button type="button" variant="secondary" onClick={onClose}>
						{t('tasklist.processesProcessTileCancelButtonLabel')}
					</Button>
					{onRetry === undefined ? null : (
						<Button type="button" onClick={onRetry}>
							{t('errorGenericErrorPageButtonLabel')}
						</Button>
					)}
				</DialogFooter>
			}
		>
			<Alert
				variant="destructive"
				title={t(isForbidden ? 'tasklist.taskDetailsProcessForbiddenTitle' : 'errorGenericErrorPageTitle')}
				description={t(subtitleKey, {processDisplayName})}
			/>
		</StartProcessFormModalShell>
	);
};

export {StartProcessFormModal, StartProcessFormModalError, StartProcessFormModalSkeleton};
export type {ErrorVariant as StartProcessFormModalErrorVariant};
