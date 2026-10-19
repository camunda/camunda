/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Text,
} from '@camunda/design-system';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';

type Props = {
	isOpen: boolean;
	onOpenChange: (isOpen: boolean) => void;
	onConfirm: (logo: string) => void;
};

const ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'];
/** The logo is kept in the browser's local storage, which holds about 5 MB for the whole site. */
const MAX_LOGO_BYTES = 512 * 1024;

function readAsDataUrl(file: File) {
	return new Promise<string>((resolve, reject) => {
		const reader = new FileReader();

		reader.onload = () => resolve(String(reader.result));
		reader.onerror = () => reject(reader.error);
		reader.readAsDataURL(file);
	});
}

/** Pick or drop an image, check it, and confirm. The image is only used after "Use logo". */
const LogoUploadDialog: React.FC<Props> = ({isOpen, onOpenChange, onConfirm}) => {
	const {t} = useTranslation();
	const [draft, setDraft] = useState<string | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [isDragging, setIsDragging] = useState(false);

	const handleOpenChange = (open: boolean) => {
		if (!open) {
			setDraft(null);
			setError(null);
			setIsDragging(false);
		}

		onOpenChange(open);
	};

	const handleFile = async (file: File | undefined) => {
		if (file === undefined) {
			return;
		}

		if (!ACCEPTED_TYPES.includes(file.type)) {
			setError(t('tasklist.themeEditorLogoTypeError'));
			return;
		}

		if (file.size > MAX_LOGO_BYTES) {
			setError(t('tasklist.themeEditorLogoSizeError'));
			return;
		}

		try {
			setDraft(await readAsDataUrl(file));
			setError(null);
		} catch {
			setError(t('tasklist.themeEditorLogoReadError'));
		}
	};

	return (
		<Dialog open={isOpen} onOpenChange={handleOpenChange}>
			<DialogContent>
				<DialogHeader>
					<DialogTitle>{t('tasklist.themeEditorLogoDialogTitle')}</DialogTitle>
					<DialogDescription>{t('tasklist.themeEditorLogoDialogDescription')}</DialogDescription>
				</DialogHeader>
				<DialogBody className="flex flex-col gap-4">
					<div
						data-testid="theme-editor-logo-dropzone"
						className={cn(
							'flex min-h-40 flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-border-strong p-6 text-center',
							isDragging && 'bg-neutral-background-medium',
						)}
						onDragOver={(event) => {
							event.preventDefault();
							setIsDragging(true);
						}}
						onDragLeave={() => setIsDragging(false)}
						onDrop={(event) => {
							event.preventDefault();
							setIsDragging(false);
							void handleFile(event.dataTransfer.files[0]);
						}}
					>
						{draft === null ? (
							<Text as="span" variant="body-sm" className="text-neutral-foreground-subtle">
								{t('tasklist.themeEditorLogoDropHint')}
							</Text>
						) : (
							<img
								src={draft}
								alt={t('tasklist.themeEditorLogoPreviewLabel')}
								className="max-h-24 max-w-full object-contain"
							/>
						)}
						{/* A label around the input opens the file chooser without any script. */}
						<label className="relative inline-flex h-8 cursor-pointer items-center rounded-lg border border-border bg-background px-3 text-sm font-medium focus-within:ring-2 focus-within:ring-ring hover:bg-neutral-background-medium">
							<input
								type="file"
								data-testid="theme-editor-logo-input"
								accept={ACCEPTED_TYPES.join(',')}
								className="absolute inset-0 size-full cursor-pointer opacity-0"
								onChange={(event) => {
									void handleFile(event.target.files?.[0]);
									event.target.value = '';
								}}
							/>
							{draft === null ? t('tasklist.themeEditorLogoChooseFile') : t('tasklist.themeEditorLogoChooseAnother')}
						</label>
					</div>
					<Text as="span" variant="helper" className="text-neutral-foreground-subtle">
						{t('tasklist.themeEditorLogoRequirements')}
					</Text>
					{error === null ? null : (
						<Text as="span" variant="helper" role="alert" className="text-danger-action-default">
							{error}
						</Text>
					)}
				</DialogBody>
				<DialogFooter showCloseButton closeButtonLabel={t('tasklist.themeEditorCancel')}>
					<Button
						type="button"
						disabled={draft === null}
						onClick={() => {
							if (draft !== null) {
								onConfirm(draft);
								handleOpenChange(false);
							}
						}}
					>
						{t('tasklist.themeEditorLogoUse')}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
};

export {LogoUploadDialog};
