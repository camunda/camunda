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
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import styles from './CollapsiblePanel.module.scss';

const BUILT_IN_FILTERS = [
	{id: 'all-open', labelKey: 'taskFilterPanelAllOpenTasks'},
	{id: 'assigned-to-me', labelKey: 'taskFilterPanelAssignedToMe'},
	{id: 'unassigned', labelKey: 'taskFilterPanelUnassigned'},
	{id: 'completed', labelKey: 'taskFilterPanelCompleted'},
] as const;

const CollapsiblePanel: React.FC = () => {
	const [isCollapsed, setIsCollapsed] = useState(true);
	const {t} = useTranslation();

	if (isCollapsed) {
		return (
			<Layer
				as="nav"
				id="task-nav-bar"
				className={cn(styles.base, styles.collapsedContainer)}
				aria-label={t('taskFilterPanelControlsAria')}
			>
				<ul aria-labelledby="task-nav-bar">
					<li>
						<Button
							hasIconOnly
							renderIcon={SidePanelOpen}
							iconDescription={t('taskFilterPanelExpandButton')}
							tooltipPosition="right"
							kind="ghost"
							size="md"
							onClick={() => {
								setIsCollapsed(false);
							}}
							aria-controls="task-nav-bar"
							aria-expanded="false"
						/>
					</li>
					<li>
						<Button
							hasIconOnly
							renderIcon={Filter}
							iconDescription={t('taskFilterPanelFilterButton')}
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
					<h1 id="filters-title">{t('taskFilterPanelTitle')}</h1>
					<Button
						hasIconOnly
						renderIcon={SidePanelClose}
						iconDescription={t('taskFilterPanelCollapse')}
						tooltipPosition="right"
						kind="ghost"
						size="md"
						onClick={() => {
							setIsCollapsed(true);
						}}
						aria-controls="task-nav-bar"
						aria-expanded="true"
					/>
				</div>
				<div className={styles.scrollContainer}>
					<ul aria-labelledby="task-nav-bar">
						{BUILT_IN_FILTERS.map(({id, labelKey}) => (
							<li key={id}>
								<span tabIndex={0} className={cn(styles.filterItem, {[styles.active!]: id === 'all-open'})}>
									{t(labelKey)}
								</span>
							</li>
						))}
					</ul>
					<ButtonSet>
						<Button kind="ghost" size="md">
							{t('taskFilterPanelNewFilter')}
						</Button>
					</ButtonSet>
				</div>
			</nav>
		</Layer>
	);
};

export {CollapsiblePanel};
