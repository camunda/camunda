/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, ButtonSet, Layer} from '@carbon/react';
import {Filter, SidePanelClose, SidePanelOpen} from '@carbon/react/icons';
import {Link, useSearch} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {FILTER_VALUES} from '#/tasklist/modules/available-tasks/searchSchema';
import styles from './CollapsiblePanel.module.scss';
import {usePrevious} from '@uidotdev/usehooks';

type BuiltInFilter = (typeof FILTER_VALUES)[number];

const BUILT_IN_FILTERS: {id: BuiltInFilter; labelKey: string}[] = [
	{id: 'all-open', labelKey: 'tasklist.taskFilterPanelAllOpenTasks'},
	{id: 'assigned-to-me', labelKey: 'tasklist.taskFilterPanelAssignedToMe'},
	{id: 'unassigned', labelKey: 'tasklist.taskFilterPanelUnassigned'},
	{id: 'completed', labelKey: 'tasklist.taskFilterPanelCompleted'},
];

const CollapsiblePanel: React.FC = () => {
	const [isCollapsed, setIsCollapsed] = useState(true);
	const wasCollapsed = usePrevious(isCollapsed);
	const {t} = useTranslation();
	const {filter} = useSearch({from: '/_auth/tasklist/_tasks'});

	if (isCollapsed) {
		return (
			<Layer
				as="nav"
				id="task-nav-bar"
				className={cn(styles.base, styles.collapsedContainer)}
				aria-label={t('tasklist.taskFilterPanelControlsAria')}
			>
				<ul aria-labelledby="task-nav-bar">
					<li>
						<Button
							hasIconOnly
							renderIcon={SidePanelOpen}
							iconDescription={t('tasklist.taskFilterPanelExpandButton')}
							tooltipPosition="right"
							kind="ghost"
							size="md"
							onClick={() => {
								setIsCollapsed(false);
							}}
							aria-controls="task-nav-bar"
							aria-expanded="false"
							autoFocus={wasCollapsed !== null && !wasCollapsed}
						/>
					</li>
					<li>
						<Button
							hasIconOnly
							renderIcon={Filter}
							iconDescription={t('tasklist.taskFilterPanelFilterButton')}
							tooltipPosition="right"
							kind="ghost"
							size="md"
						/>
					</li>
				</ul>
			</Layer>
		);
	}

	return (
		<Layer className={styles.floatingContainer}>
			<nav className={cn(styles.base, styles.expandedContainer)} id="task-nav-bar" aria-labelledby="filters-title">
				<div className={styles.panelHeader}>
					<h2 id="filters-title">{t('tasklist.taskFilterPanelTitle')}</h2>
					<Button
						hasIconOnly
						renderIcon={SidePanelClose}
						iconDescription={t('tasklist.taskFilterPanelCollapse')}
						tooltipPosition="right"
						kind="ghost"
						size="md"
						onClick={() => {
							setIsCollapsed(true);
						}}
						aria-controls="task-nav-bar"
						aria-expanded="true"
						autoFocus
					/>
				</div>
				<div className={styles.scrollContainer}>
					<ul aria-labelledby="task-nav-bar">
						{BUILT_IN_FILTERS.map(({id, labelKey}) => (
							<li key={id}>
								<Link
									to="."
									search={id === 'completed' ? {filter: id, sortBy: 'completion'} : {filter: id}}
									className={cn(styles.filterItem, {[styles.active!]: id === filter})}
									aria-current={id === filter ? 'page' : undefined}
									activeOptions={{
										includeSearch: true,
										exact: true,
									}}
								>
									{t(labelKey)}
								</Link>
							</li>
						))}
					</ul>
					<ButtonSet>
						<Button kind="ghost" size="md">
							{t('tasklist.taskFilterPanelNewFilter')}
						</Button>
					</ButtonSet>
				</div>
			</nav>
		</Layer>
	);
};

export {CollapsiblePanel};
