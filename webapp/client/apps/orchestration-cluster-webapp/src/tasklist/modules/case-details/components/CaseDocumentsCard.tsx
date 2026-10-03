/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Card, CardContent, CardHeader, CardTitle, EmptyState} from '@camunda/design-system';
import {FileText} from '@camunda/design-system/icons';
import type {CaseDocument} from '#/tasklist/modules/case-details/getCaseDocuments';
import {toHumanReadableBytes} from '#/tasklist/modules/form-js/toHumanReadableBytes';
import {endpoints} from '#/shared/http/endpoints';

type Props = {
	documents: CaseDocument[];
};

const CaseDocumentsCard: React.FC<Props> = ({documents}) => {
	const {t} = useTranslation();

	return (
		<Card>
			<CardHeader>
				<CardTitle>{t('tasklist.caseDetailsDocumentsTitle')}</CardTitle>
			</CardHeader>
			<CardContent>
				{documents.length === 0 ? (
					<EmptyState size="sm" heading={t('tasklist.caseDetailsNoDocuments')} />
				) : (
					<ul className="flex flex-col divide-y divide-border">
						{documents.map(({id, variableName, document}) => {
							const {documentId, contentHash, metadata} = document;
							const url = endpoints.getDocument({documentId, contentHash: contentHash ?? undefined}).url;

							return (
								<li key={id} className="flex items-center gap-3 py-2.5 first:pt-0 last:pb-0">
									<FileText className="size-4 shrink-0 text-[color:var(--danger-action-default)]" aria-hidden />
									<div className="flex min-w-0 flex-1 flex-col gap-0.5">
										<a
											href={url}
											target="_blank"
											rel="noopener noreferrer"
											className="truncate text-sm font-medium text-neutral-foreground-strong hover:underline"
											title={metadata.fileName}
										>
											{metadata.fileName}
										</a>
										<span className="truncate text-xs text-neutral-foreground-subtle">
											{[metadata.contentType, variableName].join(' · ')}
										</span>
									</div>
									<span className="shrink-0 text-xs text-neutral-foreground-subtle">
										{toHumanReadableBytes(metadata.size)}
									</span>
								</li>
							);
						})}
					</ul>
				)}
			</CardContent>
		</Card>
	);
};

export {CaseDocumentsCard};
