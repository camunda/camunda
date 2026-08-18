/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Card, CardContent} from '@camunda/design-system';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import type {CompletionStatus} from '#/tasklist/modules/task-details/useTaskCompletion';
import {notify} from '#/shared/notifications/toast';
import {CamundaFormRenderer, type PartialVariable} from '#/tasklist/modules/form-js/CamundaFormRenderer';
import type {FormManager} from '#/tasklist/modules/form-js/FormManager';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/components/CompleteTaskButton';
import {formatVariablesToFormData} from './formatVariablesToFormData';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';
import {useUploadDocuments} from '#/tasklist/modules/form-js/useUploadDocuments';
import styles from './TaskDetailsForm.module.scss';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';

type Props = {
	formSchema: string;
	variables: Variable[];
	completionStatus: CompletionStatus;
	isCompletionAllowed: boolean;
	isHidden: boolean;
	onSubmit: (variables: Record<string, unknown>) => void;
};

function getVariablesFromSubmitPayload(variables: PartialVariable[]) {
	return variables.reduce<Record<string, unknown>>((accumulator, {name, value}) => {
		accumulator[name] = tryParseJSON(value);
		return accumulator;
	}, {});
}

const TaskDetailsForm: React.FC<Props> = ({
	formSchema,
	variables,
	completionStatus,
	isCompletionAllowed,
	isHidden,
	onSubmit,
}) => {
	const {t} = useTranslation();
	const formManagerRef = useRef<FormManager | null>(null);
	const {mutateAsync: uploadDocuments} = useUploadDocuments();
	const [isImportError, setIsImportError] = useState(false);
	const [localSubmissionStatus, setLocalSubmissionStatus] = useState<CompletionStatus | null>(null);
	const formattedData = useMemo(() => formatVariablesToFormData(variables), [variables]);
	const submissionStatus = localSubmissionStatus ?? completionStatus;
	const canCompleteTask = isCompletionAllowed && !isImportError;

	const formRenderer = (
		<CamundaFormRenderer
			schema={formSchema}
			data={formattedData}
			readOnly={!canCompleteTask}
			onMount={(formManager) => {
				formManagerRef.current = formManager;
			}}
			handleSubmit={(variables) => {
				onSubmit(getVariablesFromSubmitPayload(variables));
				return Promise.resolve();
			}}
			handleFileUpload={(files) => uploadDocuments(files)}
			onImportError={() => {
				setIsImportError(true);
				notify({
					kind: 'error',
					title: t('tasklist.formJSInvalidSchemaErrorNotificationTitle'),
					isDismissable: true,
				});
			}}
			onSubmitStart={() => {
				setLocalSubmissionStatus('active');
			}}
			onSubmitSuccess={() => {
				setLocalSubmissionStatus(null);
			}}
			onSubmitError={() => {
				setLocalSubmissionStatus('error');
			}}
			onValidationError={() => {
				setLocalSubmissionStatus(null);
			}}
		/>
	);

	return (
		<div className={styles.container} data-testid="task-tab-content">
			<div className={styles.content} data-testid="embedded-form" tabIndex={-1}>
				{/* DS-only: the form-js output sits on the panel background with nothing
				    separating it. Wrapping it in the DS Card gives it the same raised
				    surface the task cards and the history table already have. */}
				<div className={cn(styles.form, featureFlags.dsTasklistUI && styles.formDS)}>
					{featureFlags.dsTasklistUI ? (
						<Card className={styles.formCardDS}>
							<CardContent>{formRenderer}</CardContent>
						</Card>
					) : (
						formRenderer
					)}
				</div>
				<div className={cn(styles.footer, featureFlags.dsTasklistUI && styles.footerDS)}>
					<div className={cn(styles.footerContent, featureFlags.dsTasklistUI && styles.footerContentDS)}>
						<CompleteTaskButton
							status={submissionStatus}
							onClick={() => {
								setLocalSubmissionStatus('active');
								formManagerRef.current?.submit();
							}}
							isHidden={isHidden}
							isDisabled={!canCompleteTask}
						/>
					</div>
				</div>
			</div>
		</div>
	);
};

export {TaskDetailsForm};
