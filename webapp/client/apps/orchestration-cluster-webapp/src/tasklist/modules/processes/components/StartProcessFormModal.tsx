/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState, type ReactNode} from 'react';
import {
	Button,
	ComposedModal,
	InlineNotification,
	ModalBody,
	ModalFooter,
	ModalHeader,
	ShareIcon,
} from '#/shared/design-system-compat';
import {Button as DSButton} from '@camunda/design-system';
import {Check, Link as LinkIcon, Loader2} from 'lucide-react';
// FLAG: TextInputSkeleton has no carbon-compat adapter (no shadcn Skeleton
// component shipped yet) — no DS equivalent exists, so it stays on
// @carbon/react until the DS team ships one. See
// docs/migration/human-follow-up.md ("FLAG symbols"). The skeleton rows are
// a loading placeholder only, shown for a few hundred ms before the real
// form-js schema loads, so a Carbon-styled placeholder inside an otherwise
// DS modal is a minor, temporary visual mismatch rather than a functional
// gap.
import {TextInputSkeleton} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.10';
import {CamundaFormRenderer, type PartialVariable} from '#/tasklist/modules/form-js/CamundaFormRenderer';
import type {FormManager} from '#/tasklist/modules/form-js/FormManager';
import {ProcessStartFormImportError} from '#/shared/errors';
import {featureFlags} from '#/shared/feature-flags';
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
	// A render prop rather than a plain node: the DS path relocates the share
	// button out of the header (see below) and into whichever footer wants it,
	// so the shell hands each caller the button element to place, rather than
	// the shell trying to inject a child into an opaque footer node itself.
	footer: (shareButton: ReactNode | null) => ReactNode;
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
	const shareLabel = t('tasklist.processesStartProcessWithFormShareURLAriaLabel');
	// DS-only: the footer button swaps to a "Copied" confirmation (icon +
	// label) for a couple seconds after a successful copy, then reverts —
	// per explicit request. Carbon's icon-only header button is unaffected,
	// it never reads `isCopied`.
	const [isCopied, setIsCopied] = useState(false);
	const copiedTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

	useEffect(() => {
		return () => {
			clearTimeout(copiedTimeoutRef.current);
		};
	}, []);

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

	// Carbon keeps the share button in the header, exactly as before. DS moves
	// it into the footer instead (per explicit request) — each variant's own
	// footer() call decides whether to actually place it (only
	// StartProcessFormModal, the loaded-form variant, ever passes
	// isShareButtonVisible).
	//
	// Two different Button imports on purpose: the header instance uses the
	// Carbon-shaped compat Button, unchanged from before. The footer instance
	// uses the DS-native Button directly — the compat Button's `hasIconOnly`
	// is dropped (see design-system-compat/index.ts's warnDroppedProps list),
	// so an icon-only button built through it wouldn't get the DS's own
	// square icon-button sizing.
	const headerShareButton =
		isShareButtonVisible && !featureFlags.dsTasklistUI ? (
			<Button
				kind="ghost"
				size="sm"
				hasIconOnly
				renderIcon={ShareIcon}
				iconDescription={shareLabel}
				tooltipPosition="right"
				onClick={handleShareButtonClick}
				aria-label={shareLabel}
			/>
		) : null;

	const footerShareButton =
		isShareButtonVisible && featureFlags.dsTasklistUI ? (
			<DSButton key="share" variant="ghost" size="sm" title={shareLabel} onClick={handleShareButtonClick}>
				{isCopied ? <Check aria-hidden /> : <LinkIcon aria-hidden />}
				{isCopied
					? t('tasklist.processesStartProcessWithFormCopyURLButtonLabel')
					: t('tasklist.processesStartProcessWithFormShareButtonLabel')}
			</DSButton>
		) : null;

	// The compat ComposedModal drops both `preventCloseOnClickOutside` and
	// `size` (confirmed via its own warnDroppedProps — same gap already fixed
	// the same way in JSONEditorModal.tsx). Without the outside-click guard,
	// clicking the backdrop closes the modal and discards whatever was typed
	// into the form; Radix's `onInteractOutside` reaches the DS DialogContent
	// through the compat's prop spread and can veto the dismissal. The
	// header's X and Esc still close, since those go through onOpenChange
	// rather than an outside interaction. `styles.modal` restores the width
	// `size="lg"` would have provided, and pins the header/footer so only the
	// (potentially long) embedded form scrolls — same technique
	// FieldsModal.module.scss's `.modal`/`.modalHeader`/`.modalBody` use.
	const dsModalProps = featureFlags.dsTasklistUI
		? ({
				onInteractOutside: (event: Event) => {
					event.preventDefault();
				},
				className: styles.modal,
			} as Partial<React.ComponentProps<typeof ComposedModal>>)
		: {};

	return (
		<ComposedModal open preventCloseOnClickOutside size="lg" onClose={onClose} aria-label={title} {...dsModalProps}>
			<ModalHeader
				className={featureFlags.dsTasklistUI ? styles.modalHeader : undefined}
				title={
					<div className={styles.title}>
						<span>{title}</span>
						{headerShareButton}
					</div>
				}
				iconDescription={t('tasklist.optionsModalCloseButton')}
			/>
			<ModalBody className={featureFlags.dsTasklistUI ? styles.modalBody : undefined} hasScrollingContent>
				<div className={styles.formContainer}>{children}</div>
			</ModalBody>
			{footer(footerShareButton)}
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
			footer={(shareButton) =>
				featureFlags.dsTasklistUI ? (
					// DS-only: the compat ModalFooter drops `loadingStatus` /
					// `loadingDescription` entirely (also already logged — same gap
					// hit migrating LoginPageDS.tsx's submit button), so the
					// in-progress state is hand-rolled here as a real child button
					// instead of via those props. `styles.modalFooter` pins the
					// relocated share button to the left while Cancel/Start process
					// stay grouped on the right — same grid technique
					// FieldsModal.module.scss's `.modalFooter` uses for its Reset
					// button.
					<ModalFooter className={styles.modalFooter}>
						{shareButton}
						<Button kind="secondary" type="button" onClick={onClose}>
							{t('tasklist.processesProcessTileCancelButtonLabel')}
						</Button>
						<Button
							kind="primary"
							type="button"
							disabled={isSubmitting}
							aria-busy={isSubmitting || undefined}
							onClick={() => formManagerRef.current?.submit()}
						>
							{isSubmitting ? (
								<>
									<Loader2 aria-hidden className={styles.buttonSpinner} />
									{t('tasklist.processesStartProcessPendingStatusText')}
								</>
							) : (
								t('tasklist.processesStartProcessWithFormStartButtonLabel')
							)}
						</Button>
					</ModalFooter>
				) : (
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
				)
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
			footer={() => (
				<ModalFooter
					primaryButtonText={t('tasklist.processesStartProcessWithFormStartButtonLabel')}
					primaryButtonDisabled
					secondaryButtonText={t('tasklist.processesProcessTileCancelButtonLabel')}
				>
					{null}
				</ModalFooter>
			)}
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
			footer={() => (
				<ModalFooter>
					<Button kind="secondary" onClick={onClose}>
						{t('tasklist.processesProcessTileCancelButtonLabel')}
					</Button>
					{onRetry === undefined ? null : <Button onClick={onRetry}>{t('errorGenericErrorPageButtonLabel')}</Button>}
				</ModalFooter>
			)}
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
