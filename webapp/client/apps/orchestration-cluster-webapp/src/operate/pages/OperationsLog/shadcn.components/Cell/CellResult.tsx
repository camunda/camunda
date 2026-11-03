/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {OperationsLogResultIcon} from '#/operate/shared/OperationsLogDetailsModal/shadcn.components/OperationsLogResultIcon';

type Props = {
	item: AuditLog;
};

const CellResult: React.FC<Props> = ({item}) => {
	return (
		<div className="flex items-center gap-2">
			<OperationsLogResultIcon state={item.result} data-testid={`${item.auditLogKey}-icon`} />
			<span className="sr-only">{spaceAndCapitalize(item.result)}</span>
		</div>
	);
};

export {CellResult};
