/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text} from '@camunda/design-system';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {OperationsLogResultIcon} from '#/operate/shared/OperationsLogDetailsModal/shadcn.components/OperationsLogResultIcon';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';

const CellResult: React.FC<{item: AuditLog}> = ({item}) => (
	<>
		<OperationsLogResultIcon state={item.result} />
		<Text className="sr-only">{spaceAndCapitalize(item.result)}</Text>
	</>
);

export {CellResult};
