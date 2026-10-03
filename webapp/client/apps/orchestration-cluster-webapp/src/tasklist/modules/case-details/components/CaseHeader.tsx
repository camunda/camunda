/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Link} from '@tanstack/react-router';
import {Button, Card, CardContent} from '@camunda/design-system';
import {ArrowLeft} from '@camunda/design-system/icons';
import {DateLabel} from '#/tasklist/modules/available-tasks/components/DateLabel';
import type {CaseMetadata} from '#/tasklist/modules/case-details/caseMetadataSchema';
import {CaseStatusBadge} from '#/tasklist/modules/cases/components/CaseStatusBadge';
import type {Case} from '#/tasklist/modules/cases/mapProcessInstanceToCase';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';

type Props = {
	caseItem: Case;
	header: CaseMetadata['header'];
};

const CaseHeader: React.FC<Props> = ({caseItem, header}) => {
	const {t} = useTranslation();
	const startDate = formatISODateTime(caseItem.startDate);

	return (
		<Card size="sm">
			<CardContent className="flex flex-wrap items-center gap-6">
				<div className="flex min-w-60 flex-col gap-1">
					<div className="flex items-center gap-2">
						<Button asChild variant="ghost" size="sm" className="-ml-2 h-auto px-2 py-0.5 text-xs">
							<Link to="/tasklist/cases">
								<ArrowLeft aria-hidden />
								{t('tasklist.caseDetailsBackToCases')}
							</Link>
						</Button>
						<span className="font-mono text-xs text-neutral-foreground-subtle">{caseItem.caseId}</span>
					</div>
					<h1 className="text-xl font-semibold text-neutral-foreground-strong">{caseItem.title}</h1>
					<div className="flex items-center gap-2">
						<CaseStatusBadge status={caseItem.status} />
						{startDate === null ? null : (
							<DateLabel
								date={startDate}
								relativeLabel={t('tasklist.casesStartedRelativeLabel')}
								absoluteLabel={t('tasklist.casesStartedAbsoluteLabel')}
							/>
						)}
					</div>
				</div>
				{header.length === 0 ? null : (
					<dl className="grid flex-1 grid-cols-[repeat(auto-fill,minmax(9rem,1fr))] gap-x-4 gap-y-2 border-l border-border pl-6">
						{header.map(({label, value}, index) => (
							<div key={`${label}-${index}`} className="flex min-w-0 flex-col gap-0.5">
								<dt className="truncate text-xs text-neutral-foreground-subtle">{label}</dt>
								<dd className="truncate text-sm font-medium text-neutral-foreground-strong" title={value}>
									{value}
								</dd>
							</div>
						))}
					</dl>
				)}
			</CardContent>
		</Card>
	);
};

export {CaseHeader};
