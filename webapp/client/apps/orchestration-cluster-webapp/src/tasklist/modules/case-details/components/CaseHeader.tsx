/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Link} from '@tanstack/react-router';
import type {CaseHeaderEntry} from '@camunda/case-management-sdk';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {Button, Card, CardContent, Heading, Text} from '@camunda/design-system';
import {ChevronLeft} from '@camunda/design-system/icons';
import {DateLabel} from '#/tasklist/modules/available-tasks/components/DateLabel';
import {CaseStatusBadge} from '#/tasklist/modules/cases/components/CaseStatusBadge';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';

type Props = {
	title: string;
	processName: string;
	caseId: string;
	state: ProcessInstance['state'];
	hasIncident: boolean;
	startDate: string;
	header: CaseHeaderEntry[];
};

const CaseHeader: React.FC<Props> = ({title, processName, caseId, state, hasIncident, startDate, header}) => {
	const {t} = useTranslation();
	const formattedStartDate = formatISODateTime(startDate);

	return (
		<Card size="sm">
			<CardContent className="flex flex-wrap items-center gap-6">
				<div className="flex min-w-60 flex-col gap-1">
					<div className="flex items-center gap-2">
						<Button asChild variant="ghost" size="sm" className="-ml-2 h-auto px-2 py-0.5 text-xs">
							<Link to="/tasklist/cases">
								<ChevronLeft aria-hidden />
								{t('tasklist.caseBackToCases')}
							</Link>
						</Button>
						<span className="font-mono text-xs text-neutral-foreground-subtle">{caseId}</span>
					</div>
					<Heading as="h1" variant="heading-lg">
						{title}
					</Heading>
					<div className="flex flex-wrap items-center gap-2 text-sm text-neutral-foreground-subtle">
						{title === processName ? null : <span>{processName}</span>}
						<CaseStatusBadge state={state} hasIncident={hasIncident} />
						{formattedStartDate === null ? null : (
							<DateLabel
								date={formattedStartDate}
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
								<dd className="truncate text-neutral-foreground-strong" title={value}>
									<Text variant="body-lg">{value}</Text>
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
