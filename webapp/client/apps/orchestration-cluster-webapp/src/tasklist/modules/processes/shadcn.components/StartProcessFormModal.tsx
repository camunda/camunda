/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState, type ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {Check, Link as LinkIcon} from 'lucide-react';
import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.10';
import {
	Alert,
	Button,
	Dialog,
	DialogBody,
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
			{/* Blocks backdrop-click dismissal — losing an in-progress form to a stray
			    click outside would discard whatever the user already typed. size="lg"
			    (800px) and DialogBody's own scroll-containment (only the body scrolls,
			    header/footer stay put) are both native to DialogContent — no custom
			    width/scroll CSS needed here, unlike the prototype this was ported from. */}
			{/* aria-describedby={undefined}: Radix requires DialogContent to have an
			    accessible description or an explicit opt-out — Carbon's ComposedModal
			    never had separate description text here either, only the title, so
			    there's nothing real to point to instead of inventing new copy. */}
			<DialogContent size="lg" aria-describedby={undefined} onInteractOutside={(event) => event.preventDefault()}>
				<DialogHeader>
					<DialogTitle>{t('tasklist.processesStartProcessWithForm', {processDisplayName})}</DialogTitle>
				</DialogHeader>
				<DialogBody>
					<div className="mx-auto w-full max-w-[900px]">{children}</div>
				</DialogBody>
				{footer}
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
	const [isCopied, setIsCopied] = useState(false);
	const copiedTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

	useEffect(() => {
		return () => {
			clearTimeout(copiedTimeoutRef.current);
		};
	}, []);

	if (formImportFailure !== null) {
		throw new ProcessStartFormImportError(formImportFailure.cause);
	}

	const handleShareButtonClick = useCallback(async () => {
		try {
			await navigator.clipboard.writeText(window.location.href);
			setIsCopied(true);
			clearTimeout(copiedTimeoutRef.current);
			copiedTimeoutRef.current = setTimeout(() => {
				setIsCopied(false);
			}, 2000);
		} catch (error) {
			console.error('Failed to copy URL to clipboard', error);
		}
	}, []);

	return (
		<StartProcessFormModalShell
			processDisplayName={processDisplayName}
			onClose={onClose}
			footer={
				<DialogFooter className="sm:justify-between">
					<Button type="button" variant="ghost" size="sm" onClick={handleShareButtonClick}>
						{isCopied ? <Check aria-hidden /> : <LinkIcon aria-hidden />}
						{isCopied
							? t('tasklist.processesStartProcessWithFormCopyURLButtonLabel')
							: t('tasklist.processesStartProcessWithFormShareURLAriaLabel')}
					</Button>
					<div className="flex gap-2">
						<Button type="button" variant="secondary" onClick={onClose}>
							{t('tasklist.processesProcessTileCancelButtonLabel')}
						</Button>
						<Button type="button" loading={isSubmitting} onClick={() => formManagerRef.current?.submit()}>
							{isSubmitting
								? t('tasklist.processesStartProcessPendingStatusText')
								: t('tasklist.processesStartProcessWithFormStartButtonLabel')}
						</Button>
					</div>
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
