/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link} from '@carbon/react';
import {Link as RouterLink} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {mapToCellEntityKeyData} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';

type Props = {
	item: AuditLog;
	processDefinitionName?: string | null;
	decisionDefinitionName?: string | null;
};

const CellEntityKey: React.FC<Props> = ({item, processDefinitionName, decisionDefinitionName}) => {
	const {t} = useTranslation();
	const {link, linkLabel, label, name} = mapToCellEntityKeyData(t, item, processDefinitionName, decisionDefinitionName);

	return (
		<div>
			<div>
				{item.entityType === 'USER_TASK' ? (
					<RouterLink
						to="/tasklist/$userTaskKey"
						params={{userTaskKey: item.entityKey}}
						title={linkLabel}
						aria-label={linkLabel}
					>
						{label}
					</RouterLink>
				) : link ? (
					<Link href={link} title={linkLabel} aria-label={linkLabel}>
						{label}
					</Link>
				) : (
					label
				)}
			</div>
			{item.entityDescription?.trim() || <em>{name}</em>}
		</div>
	);
};

export {CellEntityKey};
