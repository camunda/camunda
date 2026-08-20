/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button, Card, CardContent, CardFooter, CardTitle, PageHeader, PageLayout} from '@camunda/design-system';

const PLACEHOLDER_PROCESS_NAMES = ['Example process A', 'Example process B', 'Example process C'];

const PlaceholderProcessTile: React.FC<{name: string}> = ({name}) => {
	const {t} = useTranslation();

	return (
		<Card>
			<CardContent>
				<CardTitle>{name}</CardTitle>
			</CardContent>
			<CardFooter>
				<Button disabled>{t('tasklist.processesTileStartProcessButtonLabel')}</Button>
			</CardFooter>
		</Card>
	);
};

const TasklistProcessesPage: React.FC = () => {
	const {t} = useTranslation();

	return (
		<PageLayout separator>
			<PageHeader title={t('tasklist.headerNavItemProcesses')} description={t('tasklist.processesSubtitle')} />

			{/* TODO(#60227): filter bar (search + start-form + tenant dropdowns) slots in here */}

			<div className="grid grid-cols-1 gap-4 pt-6 sm:grid-cols-2 lg:grid-cols-3">
				{/* TODO(#60228): replace with the real process-tile component and real process data */}
				{PLACEHOLDER_PROCESS_NAMES.map((name) => (
					<PlaceholderProcessTile key={name} name={name} />
				))}
			</div>

			{/* TODO(#60229): start-process action / form modal mounts here via the nested route Outlet */}
		</PageLayout>
	);
};

export {TasklistProcessesPage};
