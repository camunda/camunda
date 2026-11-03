/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createLink} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {Link, Text} from '@camunda/design-system';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {mapToCellEntityKeyData} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';

const EntityLink = createLink<React.FC<React.ComponentProps<'a'>>>(Link);

const CellEntityKey: React.FC<{item: AuditLog}> = ({item}) => {
	const {t} = useTranslation();
	const {linkLabel, label, name} = mapToCellEntityKeyData(t, item);
	const linkProps = {'aria-label': linkLabel};

	const renderKey = () => {
		if (item.entityType === 'USER_TASK') {
			return (
				<EntityLink to="/tasklist/$userTaskKey" params={{userTaskKey: item.entityKey}} {...linkProps}>
					{label}
				</EntityLink>
			);
		}

		if (item.entityType === 'PROCESS_INSTANCE') {
			return (
				<EntityLink
					to="/operate/processes/$processInstanceId"
					params={{processInstanceId: item.entityKey}}
					{...linkProps}
				>
					{label}
				</EntityLink>
			);
		}

		if (item.entityType === 'BATCH' && item.batchOperationKey) {
			return (
				<EntityLink
					to="/operate/batch-operations/$batchOperationKey"
					params={{batchOperationKey: String(item.batchOperationKey)}}
					{...linkProps}
				>
					{label}
				</EntityLink>
			);
		}

		if (item.entityType === 'DECISION' && item.operationType === 'EVALUATE') {
			return (
				<EntityLink
					to="/operate/decisions/$decisionInstanceId"
					params={{decisionInstanceId: item.entityKey}}
					{...linkProps}
				>
					{label}
				</EntityLink>
			);
		}

		return label;
	};

	const description = item.entityDescription?.trim();

	return (
		<div>
			<div>{renderKey()}</div>
			{description ? <Text>{description}</Text> : <Text as="em">{name}</Text>}
		</div>
	);
};

export {CellEntityKey};
