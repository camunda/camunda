/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge, Dialog, DialogBody, DialogContent, DialogHeader, DialogTitle} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import type {DocumentInfo} from '#/operate/shared/Documents/documentInfo';
import {toHumanReadableBytes} from '#/operate/shared/Documents/humanReadableBytes';
import {middleTruncate} from '#/operate/shared/Documents/middleTruncate';
import {PreviewDocumentButton} from './PreviewDocumentButton';
import {DownloadDocumentButton} from './DownloadDocumentButton';

type Props = {
	documents: DocumentInfo[];
	labelSuffix: string;
	onClose: () => void;
	loadingHint?: string;
	errorHint?: string;
};

const DocumentListModal: React.FC<Props> = ({documents, labelSuffix, onClose, loadingHint, errorHint}) => {
	const {t} = useTranslation();
	const isFullyLoaded = !loadingHint && !errorHint;
	const count = documents.length;
	const amount = isFullyLoaded ? `${count}` : `${count}+`;

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent size="md" aria-describedby={undefined} closeLabel={t('operate.shared.editors.close')}>
				<DialogHeader>
					<DialogTitle>
						{t(
							count !== 1 ? 'operate.shared.documents.listTitleMultiple' : 'operate.shared.documents.listTitleSingle',
							{amount, label: labelSuffix},
						)}
					</DialogTitle>
				</DialogHeader>
				<DialogBody>
					<ul className="m-0 flex list-none flex-col p-0">
						{documents.map((document, index) => (
							<li
								key={index}
								aria-labelledby={`document-item-${index}`}
								className="flex items-center gap-2 border-b border-border py-3 last:border-b-0"
							>
								<div className="grid min-w-0 flex-1 grid-flow-col grid-cols-[auto_1fr] grid-rows-2 justify-items-start gap-x-2 gap-y-1">
									<span
										id={`document-item-${index}`}
										title={document.fileName}
										className="overflow-hidden text-sm text-ellipsis whitespace-nowrap"
									>
										{middleTruncate(document.fileName)}
									</span>
									<span className="text-xs text-neutral-foreground-subtle">{toHumanReadableBytes(document.size)}</span>
									{document.isExpired && <Badge variant="danger">{t('operate.shared.documents.expired')}</Badge>}
								</div>
								<PreviewDocumentButton document={document} labelSuffix={labelSuffix} />
								<DownloadDocumentButton document={document} labelSuffix={labelSuffix} />
							</li>
						))}
					</ul>
					{loadingHint && <p className="mt-2 text-center text-sm text-neutral-foreground-subtle">{loadingHint}</p>}
					{errorHint && <p className="mt-2 text-center text-sm text-neutral-foreground-subtle">{errorHint}</p>}
				</DialogBody>
			</DialogContent>
		</Dialog>
	);
};

export {DocumentListModal};
