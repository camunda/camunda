/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Button,
	CodeBlock,
	Dialog,
	DialogBody,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	DialogTrigger,
	InlineCode,
	Text,
} from '@camunda/design-system';
import {Download} from '@camunda/design-system/icons';
import {observer} from 'mobx-react-lite';
import {useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import {themeEditorStore} from '#/tasklist/modules/theme-editor/themeEditorStore';

const ExportDialog: React.FC = observer(() => {
	const {t} = useTranslation();
	const {customCss} = themeEditorStore;

	const handleDownload = useCallback(() => {
		const url = URL.createObjectURL(new Blob([customCss], {type: 'text/css'}));
		const link = document.createElement('a');

		link.href = url;
		link.download = 'custom.css';
		link.click();
		URL.revokeObjectURL(url);
	}, [customCss]);

	return (
		<Dialog>
			<DialogTrigger asChild>
				<Button type="button" className="w-full">
					{t('tasklist.themeEditorGetCss')}
				</Button>
			</DialogTrigger>
			<DialogContent size="lg">
				<DialogHeader>
					<DialogTitle>{t('tasklist.themeEditorExportTitle')}</DialogTitle>
					<DialogDescription>{t('tasklist.themeEditorExportDescription')}</DialogDescription>
				</DialogHeader>
				<DialogBody className="flex flex-col gap-4">
					<CodeBlock
						variant="multi"
						copyable
						copyLabel={t('tasklist.themeEditorCopy')}
						copiedLabel={t('tasklist.themeEditorCopied')}
						className="max-h-96 overflow-auto"
					>
						{customCss}
					</CodeBlock>
					<div className="flex flex-col gap-1">
						<Text variant="body-sm">
							{t('tasklist.themeEditorExportDocker')} <InlineCode>/usr/local/camunda/config/custom.css</InlineCode>
						</Text>
						<Text variant="body-sm">
							{t('tasklist.themeEditorExportArchive')}{' '}
							<InlineCode>camunda-zeebe-&lt;version&gt;/config/custom.css</InlineCode>
						</Text>
						<Text variant="body-sm" className="text-neutral-foreground-subtle">
							{t('tasklist.themeEditorExportRestart')}
						</Text>
					</div>
				</DialogBody>
				<DialogFooter showCloseButton closeButtonLabel={t('tasklist.themeEditorClose')}>
					<Button type="button" onClick={handleDownload}>
						<Download aria-hidden />
						{t('tasklist.themeEditorDownload')}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
});

export {ExportDialog};
