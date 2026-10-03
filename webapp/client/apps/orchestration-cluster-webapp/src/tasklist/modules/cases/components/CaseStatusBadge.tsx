/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';
import type {CaseStatus} from '#/tasklist/modules/cases/mapProcessInstanceToCase';

const STATUS_MAPPINGS = {
	active: {variant: 'success', labelKey: 'tasklist.casesActive'},
	incident: {variant: 'danger', labelKey: 'tasklist.casesIncident'},
} as const satisfies Record<CaseStatus, {variant: string; labelKey: string}>;

type Props = {
	status: CaseStatus;
};

const CaseStatusBadge: React.FC<Props> = ({status}) => {
	const {t} = useTranslation();
	const {variant, labelKey} = STATUS_MAPPINGS[status];

	return <Badge variant={variant}>{t(labelKey)}</Badge>;
};

export {CaseStatusBadge};
