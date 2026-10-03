/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge, Card, Collapsible, CollapsibleContent, CollapsibleTrigger} from '@camunda/design-system';
import {ChevronDown, History} from '@camunda/design-system/icons';
import type {CaseHistoryEntry, CaseHistoryKind} from '#/tasklist/modules/case-details/getCaseHistory';
import {formatHistoryDate} from '#/tasklist/modules/task-details-history/formatHistoryDate';

const KIND_MAPPINGS = {
	opened: {variant: 'neutral', labelKey: 'tasklist.caseDetailsHistoryKindOpened'},
	closed: {variant: 'neutral', labelKey: 'tasklist.caseDetailsHistoryKindClosed'},
	step: {variant: 'info', labelKey: 'tasklist.caseDetailsHistoryKindStep'},
	task: {variant: 'success', labelKey: 'tasklist.caseDetailsHistoryKindTask'},
	incident: {variant: 'danger', labelKey: 'tasklist.caseDetailsHistoryKindIncident'},
} as const satisfies Record<CaseHistoryKind, {variant: string; labelKey: string}>;

type Props = {
	entries: CaseHistoryEntry[];
};

const CaseHistory: React.FC<Props> = ({entries}) => {
	const {t} = useTranslation();

	return (
		<Card className="gap-0 py-0">
			<Collapsible>
				<CollapsibleTrigger className="group flex w-full items-center gap-2 px-5 py-4 text-left">
					<History className="size-4 shrink-0 text-neutral-foreground-subtle" aria-hidden />
					<span className="flex-1 text-sm font-semibold text-neutral-foreground-strong">
						{t('tasklist.caseDetailsHistoryTitle')}
					</span>
					<span className="text-xs text-neutral-foreground-subtle">
						{t('tasklist.caseDetailsHistoryCount', {count: entries.length})}
					</span>
					<ChevronDown
						className="size-4 shrink-0 text-neutral-foreground-subtle transition-transform group-data-[state=open]:rotate-180"
						aria-hidden
					/>
				</CollapsibleTrigger>
				<CollapsibleContent>
					<ol className="flex flex-col border-t border-border px-5 py-3">
						{entries.map((entry, index) => {
							const {variant, labelKey} = KIND_MAPPINGS[entry.kind];

							return (
								<li key={entry.id} className="flex gap-3">
									<div className="flex w-2 shrink-0 flex-col items-center pt-2">
										<span className="size-2 shrink-0 rounded-full bg-neutral-foreground-subtle" />
										{index === entries.length - 1 ? null : <span className="w-px flex-1 bg-border" />}
									</div>
									<div className="flex min-w-0 flex-col gap-0.5 pb-4">
										<div className="flex items-center gap-2">
											<Badge variant={variant}>{t(labelKey)}</Badge>
											<span className="text-xs text-neutral-foreground-subtle">
												{formatHistoryDate(entry.timestamp)}
											</span>
										</div>
										<span className="text-sm text-neutral-foreground-strong">{entry.title}</span>
										{entry.description === null ? null : (
											<span className="text-xs text-neutral-foreground-subtle">{entry.description}</span>
										)}
									</div>
								</li>
							);
						})}
					</ol>
				</CollapsibleContent>
			</Collapsible>
		</Card>
	);
};

export {CaseHistory};
