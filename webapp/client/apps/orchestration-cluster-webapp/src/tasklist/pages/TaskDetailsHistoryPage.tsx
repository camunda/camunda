/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Layer} from '#/shared/design-system-compat';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import type {TaskDetailsHistorySearch} from '#/tasklist/modules/task-details-history/sortUtils';
import {HistoryTable} from '#/tasklist/modules/task-details-history/components/HistoryTable';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import styles from './TaskDetailsHistoryPage.module.scss';

type Props = {
	userTaskKey: string;
	auditLogs: AuditLog[];
	search: TaskDetailsHistorySearch;
	onScrollDown: () => void;
};

const TaskDetailsHistoryPage: React.FC<Props> = ({userTaskKey, auditLogs, search, onScrollDown}) => {
	const {t} = useTranslation();

	const handleScroll: React.UIEventHandler<HTMLDivElement> = (event) => {
		const target = event.currentTarget;
		const {scrollTop, scrollHeight, clientHeight} = target;
		const isAtBottom = Math.floor(scrollHeight - clientHeight - scrollTop) <= 1;

		if (isAtBottom) {
			onScrollDown();
		}
	};

	if (auditLogs.length === 0) {
		return (
			<div
				className={cn(styles.container, featureFlags.dsTasklistUI && styles.containerDS)}
				data-testid="history-tab-content"
			>
				<div className={cn(styles.emptyContainer, featureFlags.dsTasklistUI && styles.emptyContainerDS)}>
					<Layer>
						<p>{t('tasklist.taskDetailsHistoryEmptyMessage')}</p>
					</Layer>
				</div>
			</div>
		);
	}

	return (
		<div
			className={cn(styles.container, featureFlags.dsTasklistUI && styles.containerDS)}
			data-testid="history-tab-content"
		>
			<div
				className={cn(styles.tableContainer, featureFlags.dsTasklistUI && styles.tableContainerDS)}
				data-testid="history-scroll-container"
				onScroll={handleScroll}
			>
				<HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={search} />
			</div>
		</div>
	);
};

export {TaskDetailsHistoryPage};
