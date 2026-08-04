/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {isValidProcessInstanceKey} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';

type Props = {
	item: AuditLog;
	processDefinitionName?: string | null;
};

const CellParentEntity: React.FC<Props> = ({item, processDefinitionName}) => {
	const {t} = useTranslation();

	switch (item.entityType) {
		case 'USER_TASK':
		case 'INCIDENT':
		case 'VARIABLE':
			return isValidProcessInstanceKey(item.processInstanceKey) ? (
				<div>
					<div>
						<Link
							href={`/operate/processes/${item.processInstanceKey}`}
							aria-label={t('operate.operationsLog.entityLinks.viewProcessInstance', {key: item.processInstanceKey})}
						>
							{item.processInstanceKey}
						</Link>
					</div>
					<em>{processDefinitionName}</em>
				</div>
			) : (
				'-'
			);
		default:
			return '-';
	}
};

export {CellParentEntity};
