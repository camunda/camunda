/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button, MultiSelect, type MultiSelectOption} from '@camunda/design-system';
import type {IncidentErrorType} from '@camunda/camunda-api-zod-schemas/8.11';
import {useIncidentsFilter} from '../useIncidentsFilter';
import {INCIDENT_ERROR_TYPES} from '../incidentErrorTypes';

const IncidentsFilter: React.FC = () => {
	const {t} = useTranslation();
	const {errorTypes, setErrorTypes} = useIncidentsFilter();
	const options: MultiSelectOption[] = INCIDENT_ERROR_TYPES.map((errorType) => ({
		value: errorType,
		label: t(`operate.processInstance.incidents.errorTypes.${errorType}`),
	}));

	return (
		<div className="ml-auto flex items-center gap-2">
			<MultiSelect
				id="incidents-by-errorType"
				className="w-72"
				size="sm"
				options={options}
				value={errorTypes}
				placeholder={t('operate.processInstance.incidents.filterByType')}
				aria-label={t('operate.processInstance.incidents.filterByType')}
				onValueChange={(value) => setErrorTypes(value as IncidentErrorType[])}
			/>
			<Button
				type="button"
				variant="ghost"
				size="sm"
				disabled={errorTypes.length === 0}
				onClick={() => setErrorTypes([])}
			>
				{t('operate.processInstance.incidents.resetFilters')}
			</Button>
		</div>
	);
};

export {IncidentsFilter};
