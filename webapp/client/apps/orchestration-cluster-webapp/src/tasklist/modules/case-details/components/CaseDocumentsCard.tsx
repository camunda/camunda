/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {DocumentReference} from '@camunda/case-management-sdk';
import {Card, CardContent, CardHeader, CardTitle, EmptyState, Text} from '@camunda/design-system';
import {FileText} from '@camunda/design-system/icons';
import {endpoints} from '#/shared/http/endpoints';
import {toHumanReadableBytes} from '#/tasklist/modules/form-js/toHumanReadableBytes';

type Props = {
	documents: DocumentReference[];
};

const CaseDocumentsCard: React.FC<Props> = ({documents}) => {
	const {t} = useTranslation();

	return (
		<Card>
			<CardHeader>
				<CardTitle>{t('tasklist.caseDocumentsTitle')}</CardTitle>
			</CardHeader>
			<CardContent>
				{documents.length === 0 ? (
					<EmptyState size="sm" heading={t('tasklist.caseNoDocuments')} />
				) : (
					<ul className="flex flex-col divide-y divide-border">
						{documents.map(({variableName, documentId, contentHash, fileName, contentType, size}) => (
							<li key={`${variableName}-${documentId}`} className="flex items-center gap-3 py-2.5 first:pt-0 last:pb-0">
								<FileText className="size-4 shrink-0 text-danger-foreground-strong" aria-hidden />
								<div className="flex min-w-0 flex-1 flex-col gap-0.5">
									<a
										href={endpoints.getDocument({documentId, contentHash: contentHash ?? undefined}).url}
										target="_blank"
										rel="noopener noreferrer"
										className="truncate text-neutral-foreground-strong hover:underline"
										title={fileName}
									>
										<Text variant="label-md-strong">{fileName}</Text>
									</a>
									<span className="truncate text-xs text-neutral-foreground-subtle">
										{[contentType, variableName].filter(Boolean).join(' · ')}
									</span>
								</div>
								{size === null ? null : (
									<span className="shrink-0 text-xs text-neutral-foreground-subtle">{toHumanReadableBytes(size)}</span>
								)}
							</li>
						))}
					</ul>
				)}
			</CardContent>
		</Card>
	);
};

export {CaseDocumentsCard};
