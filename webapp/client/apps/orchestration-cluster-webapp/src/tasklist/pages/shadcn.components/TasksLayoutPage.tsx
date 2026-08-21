/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {EmptyState} from '@camunda/design-system';
import {Outlet} from '@tanstack/react-router';
import {Search} from 'lucide-react';
import {useTranslation} from 'react-i18next';

const TasksLayoutPage: React.FC = () => {
	const {t} = useTranslation();

	return (
		<main id="main-content" className="grid h-full grid-cols-[19.5rem_minmax(0,1fr)] overflow-hidden">
			<section
				className="grid grid-rows-[3rem_minmax(0,1fr)] overflow-hidden"
				aria-label={t('tasklist.tasksPanelLabel')}
			>
				<header className="flex items-center border-b border-border px-4">
					<h1 className="sr-only">{t('tasklist.headerNavItemTasks')}</h1>
				</header>
				<div className="overflow-auto p-4">
					<EmptyState
						size="sm"
						icon={<Search aria-hidden />}
						heading={t('tasklist.availableTasksNoTasksFoundInfo')}
						description={t('tasklist.availableTasksNoTasksMatchingCriteriaInfo')}
					/>
				</div>
			</section>
			<section className="overflow-auto border-l border-border" aria-label={t('tasklist.taskDetailsDetailsLabel')}>
				<Outlet />
			</section>
		</main>
	);
};

export {TasksLayoutPage};
