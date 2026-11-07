/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';

const STATE_MAPPINGS = {
	ACTIVE: {variant: 'success', labelKey: 'tasklist.casesStatusActive'},
	SUSPENDED: {variant: 'warning', labelKey: 'tasklist.casesStatusSuspended'},
	COMPLETED: {variant: 'neutral', labelKey: 'tasklist.casesStatusCompleted'},
	TERMINATED: {variant: 'neutral', labelKey: 'tasklist.casesStatusTerminated'},
} as const satisfies Record<ProcessInstance['state'], {variant: string; labelKey: string}>;

type Props = Pick<ProcessInstance, 'state' | 'hasIncident'>;

const CaseStatusBadge: React.FC<Props> = ({state, hasIncident}) => {
	const {t} = useTranslation();

	if (hasIncident && state === 'ACTIVE') {
		return <Badge variant="danger">{t('tasklist.casesStatusIncident')}</Badge>;
	}

	const {variant, labelKey} = STATE_MAPPINGS[state];

	return <Badge variant={variant}>{t(labelKey)}</Badge>;
};

export {CaseStatusBadge};
