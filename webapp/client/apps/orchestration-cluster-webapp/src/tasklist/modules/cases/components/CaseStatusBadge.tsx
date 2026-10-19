/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';

type Props = {
	hasIncident: boolean;
};

const CaseStatusBadge: React.FC<Props> = ({hasIncident}) => {
	const {t} = useTranslation();

	return hasIncident ? (
		<Badge variant="danger">{t('tasklist.casesStatusIncident')}</Badge>
	) : (
		<Badge variant="success">{t('tasklist.casesStatusActive')}</Badge>
	);
};

export {CaseStatusBadge};
