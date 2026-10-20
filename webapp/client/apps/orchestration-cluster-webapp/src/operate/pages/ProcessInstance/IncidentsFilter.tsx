/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button, MultiSelect} from '@carbon/react';
import type {IncidentErrorType} from '@camunda/camunda-api-zod-schemas/8.11';
import {useIncidentsFilter} from './useIncidentsFilter';
import {FilterContainer, FilterLayer, FilterStack} from './incidents.styled';

const INCIDENT_ERROR_TYPES: IncidentErrorType[] = [
	'UNSPECIFIED',
	'UNKNOWN',
	'IO_MAPPING_ERROR',
	'JOB_NO_RETRIES',
	'EXECUTION_LISTENER_NO_RETRIES',
	'TASK_LISTENER_NO_RETRIES',
	'CONDITION_ERROR',
	'EXTRACT_VALUE_ERROR',
	'CALLED_ELEMENT_ERROR',
	'UNHANDLED_ERROR_EVENT',
	'MESSAGE_SIZE_EXCEEDED',
	'CALLED_DECISION_ERROR',
	'DECISION_EVALUATION_ERROR',
	'FORM_NOT_FOUND',
	'RESOURCE_NOT_FOUND',
	'AD_HOC_SUB_PROCESS_NO_RETRIES',
	'SECRET_RESOLUTION_ERROR',
];

const IncidentsFilter: React.FC = () => {
	const {t} = useTranslation();
	const {errorTypes, setErrorTypes} = useIncidentsFilter();

	return (
		<FilterLayer>
			<FilterContainer>
				<FilterStack orientation="horizontal" gap={5}>
					<MultiSelect<IncidentErrorType>
						id="incidents-by-errorType"
						items={INCIDENT_ERROR_TYPES}
						selectedItems={errorTypes}
						itemToString={(errorType) =>
							errorType ? t(`operate.processInstance.incidents.errorTypes.${errorType}`) : ''
						}
						label={t('operate.processInstance.incidents.filterByType')}
						titleText={t('operate.processInstance.incidents.filterByType')}
						hideLabel
						onChange={({selectedItems}) => setErrorTypes(selectedItems ?? [])}
						size="sm"
					/>
					<Button
						kind="ghost"
						onClick={() => setErrorTypes([])}
						disabled={errorTypes.length === 0}
						title={t('operate.processInstance.incidents.resetFilters')}
						size="sm"
					>
						{t('operate.processInstance.incidents.resetFilters')}
					</Button>
				</FilterStack>
			</FilterContainer>
		</FilterLayer>
	);
};

export {IncidentsFilter};
