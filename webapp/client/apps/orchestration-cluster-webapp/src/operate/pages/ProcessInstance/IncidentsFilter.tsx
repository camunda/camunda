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
import {INCIDENT_ERROR_TYPES} from './incidentErrorTypes';
import {FilterContainer, FilterLayer, FilterStack} from './incidents.styled';

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
