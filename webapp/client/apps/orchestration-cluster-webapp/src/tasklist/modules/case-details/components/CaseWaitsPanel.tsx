/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Collapsible, CollapsibleContent, CollapsibleTrigger} from '@camunda/design-system';
import {ChevronDown, Clock} from '@camunda/design-system/icons';
import type {CaseWait} from '#/tasklist/modules/case-details/getCaseWaits';
import {WaitStateIcon} from '#/tasklist/modules/cases/components/WaitStateIcon';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';

type Props = {
	waits: CaseWait[];
};

const CaseWaitsPanel: React.FC<Props> = ({waits}) => {
	const {t} = useTranslation();

	return (
		<Collapsible className="border-t border-border bg-neutral-background-subtle">
			<CollapsibleTrigger className="group flex w-full items-center gap-2 px-5 py-2.5 text-left text-sm">
				<Clock className="size-4 shrink-0 text-neutral-foreground-subtle" aria-hidden />
				<span className="flex-1 text-neutral-foreground-strong">
					{t('tasklist.caseDetailsWaitingOn', {count: waits.length})}
				</span>
				<ChevronDown
					className="size-4 shrink-0 text-neutral-foreground-subtle transition-transform group-data-[state=open]:rotate-180"
					aria-hidden
				/>
			</CollapsibleTrigger>
			<CollapsibleContent>
				{waits.length === 0 ? (
					<p className="px-5 pb-4 text-sm text-neutral-foreground-subtle">{t('tasklist.caseDetailsNothingWaiting')}</p>
				) : (
					<ul className="grid grid-cols-[repeat(auto-fill,minmax(16rem,1fr))] gap-2 px-5 pb-4">
						{waits.map((wait) => {
							const since = wait.since === null ? null : formatISODateTime(wait.since)?.relative.text;
							const secondary = [wait.assignment ?? wait.kindLabel, ...wait.extras].join(' · ');

							return (
								<li
									key={wait.id}
									className="flex items-center gap-3 rounded-lg border border-border bg-background px-3 py-2.5"
								>
									<WaitStateIcon kind={wait.kind} label={wait.kindLabel} />
									<div className="flex min-w-0 flex-1 flex-col gap-0.5">
										<span className="truncate text-sm font-medium text-neutral-foreground-strong">{wait.title}</span>
										<span className="truncate text-xs text-neutral-foreground-subtle" title={secondary}>
											{secondary}
										</span>
									</div>
									{since === null || since === undefined ? null : (
										<span className="shrink-0 text-xs text-neutral-foreground-subtle">{since}</span>
									)}
								</li>
							);
						})}
					</ul>
				)}
			</CollapsibleContent>
		</Collapsible>
	);
};

export {CaseWaitsPanel};
