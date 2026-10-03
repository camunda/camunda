/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyState} from '@camunda/design-system';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {CamundaFormRenderer} from '#/tasklist/modules/form-js/CamundaFormRenderer';
import {formatVariablesToFormData} from '#/tasklist/modules/task-details-form/formatVariablesToFormData';

const handleReadOnlySubmit = () => Promise.resolve();

type Props = {
	formSchema: string | null;
	variables: Variable[];
};

const CaseFormCard: React.FC<Props> = ({formSchema, variables}) => {
	const {t} = useTranslation();
	const [isImportError, setIsImportError] = useState(false);
	const data = useMemo(() => formatVariablesToFormData(variables), [variables]);
	const handleImportError = useCallback(() => setIsImportError(true), []);

	return (
		<Card>
			<CardHeader>
				<CardTitle>{t('tasklist.caseDetailsInformationTitle')}</CardTitle>
				<CardDescription>{t('tasklist.caseDetailsInformationDescription')}</CardDescription>
			</CardHeader>
			<CardContent>
				{formSchema === null || isImportError ? (
					<EmptyState
						size="sm"
						heading={isImportError ? t('tasklist.caseDetailsInvalidForm') : t('tasklist.caseDetailsNoForm')}
					/>
				) : (
					<CamundaFormRenderer
						schema={formSchema}
						data={data}
						readOnly
						handleSubmit={handleReadOnlySubmit}
						onImportError={handleImportError}
					/>
				)}
			</CardContent>
		</Card>
	);
};

export {CaseFormCard};
