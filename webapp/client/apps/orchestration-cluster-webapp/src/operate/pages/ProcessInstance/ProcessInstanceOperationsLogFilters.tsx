/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isEqual} from 'lodash';
import {Form} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import {Stack} from '@carbon/react';
import {
	auditLogEntityTypeSchema,
	auditLogOperationTypeSchema,
	type AuditLogEntityType,
	type AuditLogOperationType,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {AutoSubmit} from '#/operate/shared/AutoSubmit/AutoSubmit';
import {FilterMultiSelect} from '#/operate/shared/FilterMultiSelect/FilterMultiSelect';
import type {ProcessInstanceOperationsLogSearch} from './processInstanceOperationsLog.schema';
import {FiltersForm} from './processInstanceOperationsLog.styled';

type FormValues = {
	operationType?: AuditLogOperationType[];
	entityType?: AuditLogEntityType[];
};

const ProcessInstanceOperationsLogFilters: React.FC<{search: ProcessInstanceOperationsLogSearch}> = ({search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();

	return (
		<Form<FormValues>
			onSubmit={(values) => {
				void navigate({
					to: '.',
					search: (prev) => ({
						...prev,
						operationType: values.operationType?.length ? values.operationType : undefined,
						entityType: values.entityType?.length ? values.entityType : undefined,
					}),
				});
			}}
			initialValues={{operationType: search.operationType, entityType: search.entityType}}
			initialValuesEqual={isEqual}
		>
			{({handleSubmit}) => (
				<FiltersForm onSubmit={handleSubmit}>
					<AutoSubmit fieldsToSkipTimeout={['operationType', 'entityType']} />
					<Stack orientation="horizontal" gap={5}>
						<FilterMultiSelect
							name="operationType"
							titleText={t('operate.operationsLog.filters.operationType')}
							items={auditLogOperationTypeSchema.options}
						/>
						<FilterMultiSelect
							name="entityType"
							titleText={t('operate.operationsLog.filters.entityType')}
							items={auditLogEntityTypeSchema.options}
						/>
					</Stack>
				</FiltersForm>
			)}
		</Form>
	);
};

export {ProcessInstanceOperationsLogFilters};
