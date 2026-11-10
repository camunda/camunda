/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OPERATE_BASE} from '#/shared/operateBase';
import {createLink} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {Link} from '@camunda/design-system';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {mapToCellEntityKeyData} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';

type Props = {
	item: AuditLog;
	processDefinitionName?: string | null;
	decisionDefinitionName?: string | null;
};

const EntityLink = createLink<React.FC<React.ComponentProps<'a'>>>(Link);

const CellEntityKey: React.FC<Props> = ({item, processDefinitionName, decisionDefinitionName}) => {
	const {t} = useTranslation();
	const {linkLabel, label, name} = mapToCellEntityKeyData(t, item, processDefinitionName, decisionDefinitionName);

	const renderKey = () => {
		if (item.entityType === 'USER_TASK') {
			return (
				<EntityLink
					to="/tasklist/$userTaskKey"
					params={{userTaskKey: item.entityKey}}
					title={linkLabel}
					aria-label={linkLabel}
				>
					{label}
				</EntityLink>
			);
		}
		if (item.entityType === 'PROCESS_INSTANCE') {
			return (
				<EntityLink
					to={`${OPERATE_BASE}/processes/$processInstanceId` as const}
					params={{processInstanceId: item.entityKey}}
					title={linkLabel}
					aria-label={linkLabel}
				>
					{label}
				</EntityLink>
			);
		}
		if (item.entityType === 'BATCH' && item.batchOperationKey) {
			return (
				<EntityLink
					to={`${OPERATE_BASE}/batch-operations/$batchOperationKey` as const}
					params={{batchOperationKey: String(item.batchOperationKey)}}
					title={linkLabel}
					aria-label={linkLabel}
				>
					{label}
				</EntityLink>
			);
		}
		if (item.entityType === 'DECISION' && item.operationType === 'EVALUATE') {
			return (
				<EntityLink
					to={`${OPERATE_BASE}/decisions/$decisionInstanceId` as const}
					params={{decisionInstanceId: item.entityKey}}
					title={linkLabel}
					aria-label={linkLabel}
				>
					{label}
				</EntityLink>
			);
		}
		return label;
	};

	return (
		<div>
			<div>{renderKey()}</div>
			{item.entityDescription?.trim() || <em>{name}</em>}
		</div>
	);
};

export {CellEntityKey};
