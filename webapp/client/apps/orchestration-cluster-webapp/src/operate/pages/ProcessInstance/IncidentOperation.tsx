/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useIsMutating} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {InlineLoading} from '@carbon/react';
import {OperationItems} from '#/operate/shared/OperationItems/OperationItems';
import {OperationItem} from '#/operate/shared/OperationItem/OperationItem';
import {useResolveIncident} from './useResolveIncident';
import {OperationContainer} from './incidents.styled';

type Props = {
	incidentKey: string;
	jobKey: string | null;
	disabled: boolean;
};

const IncidentOperation: React.FC<Props> = ({incidentKey, jobKey, disabled}) => {
	const {t} = useTranslation();
	const {mutate, mutationKey} = useResolveIncident({incidentKey, jobKey});
	const isRetryPending = useIsMutating({mutationKey, exact: true}) > 0;

	return (
		<OperationContainer orientation="horizontal">
			{isRetryPending && <InlineLoading data-testid="operation-spinner" />}
			<OperationItems>
				<OperationItem
					type="RESOLVE_INCIDENT"
					onClick={(event) => {
						event.stopPropagation();
						mutate();
					}}
					title={t('operate.processInstance.incidents.retryIncident')}
					disabled={isRetryPending || disabled}
					size="sm"
				/>
			</OperationItems>
		</OperationContainer>
	);
};

export {IncidentOperation};
