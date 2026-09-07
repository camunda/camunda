/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import type {TaskDetailsHistorySearch} from '#/tasklist/modules/task-details-history/sortUtils';
import {HistoryTable} from '#/tasklist/modules/task-details-history/shadcn.components/HistoryTable';

type Props = {
	userTaskKey: string;
	auditLogs: AuditLog[];
	search: TaskDetailsHistorySearch;
	onScrollDown: () => void;
};

const TaskDetailsHistoryPage: React.FC<Props> = ({userTaskKey, auditLogs, search, onScrollDown}) => {
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
			<div className="flex h-full min-h-0 w-full flex-col pt-4" data-testid="history-tab-content">
				<div className="min-h-0 w-full flex-1 overflow-auto px-4 pb-4">
					<HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={search} />
				</div>
			</div>
		);
	}

	return (
		<div className="flex h-full min-h-0 w-full flex-col pt-4" data-testid="history-tab-content">
			<div
				className="min-h-0 w-full flex-1 overflow-auto px-4 pb-4"
				data-testid="history-scroll-container"
				onScroll={handleScroll}
			>
				<HistoryTable userTaskKey={userTaskKey} auditLogs={auditLogs} search={search} />
			</div>
		</div>
	);
};

export {TaskDetailsHistoryPage};
