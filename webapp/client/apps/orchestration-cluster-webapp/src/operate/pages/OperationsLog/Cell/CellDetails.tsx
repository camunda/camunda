/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {mapToCellDetailsData} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';
import {PropertyText} from '../styled';

type Props = {
	item: AuditLog;
};

const CellDetails: React.FC<Props> = ({item}) => {
	const {t} = useTranslation();
	const {property, value} = mapToCellDetailsData(t, item);

	return property ? (
		<>
			<PropertyText>{property}</PropertyText>
			{value}
		</>
	) : (
		'-'
	);
};

export {CellDetails};
