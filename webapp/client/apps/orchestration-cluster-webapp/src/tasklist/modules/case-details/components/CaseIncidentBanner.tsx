/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Alert, AlertDescription, AlertTitle} from '@camunda/design-system';
import {TriangleAlert} from '@camunda/design-system/icons';

const CaseIncidentBanner: React.FC = () => {
	const {t} = useTranslation();

	return (
		<Alert variant="destructive">
			<TriangleAlert aria-hidden />
			<AlertTitle>{t('tasklist.caseIncidentTitle')}</AlertTitle>
			<AlertDescription>{t('tasklist.caseIncidentDescription')}</AlertDescription>
		</Alert>
	);
};

export {CaseIncidentBanner};
