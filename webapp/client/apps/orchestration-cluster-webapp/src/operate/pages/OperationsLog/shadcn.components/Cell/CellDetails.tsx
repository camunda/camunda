/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Text} from '@camunda/design-system';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {mapToCellDetailsData} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';

type Props = {
	item: AuditLog;
};

const CellDetails: React.FC<Props> = ({item}) => {
	const {t} = useTranslation();
	const {property, value} = mapToCellDetailsData(t, item);

	return property ? (
		<>
			<Text as="div" variant="body-sm" className="text-neutral-foreground-subtle">
				{property}
			</Text>
			{value}
		</>
	) : (
		'-'
	);
};

export {CellDetails};
