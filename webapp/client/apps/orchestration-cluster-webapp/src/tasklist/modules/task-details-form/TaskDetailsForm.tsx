/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import type {CompletionStatus} from '#/tasklist/modules/task-details/useTaskCompletion';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {CamundaFormRenderer, type PartialVariable} from '#/tasklist/modules/form-js/CamundaFormRenderer';
import type {FormManager} from '#/tasklist/modules/form-js/FormManager';
import {formatVariablesToFormData} from '#/tasklist/modules/form-js/formatVariablesToFormData';
import {tryParseJSON} from '#/tasklist/modules/form-js/tryParseJSON';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/components/CompleteTaskButton';
import {useUploadDocuments} from './useUploadDocuments';
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
	return variables.reduce<Record<string, unknown>>(
		(accumulator, {name, value}) => ({
			...accumulator,
			[name]: tryParseJSON(value),
		}),
		{},
	);
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

	return (
		<div className={styles.container} data-testid="task-tab-content">
			<div className={styles.content} data-testid="embedded-form" tabIndex={-1}>
				<div className={styles.form}>
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
							notificationsStore.displayNotification({
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
				</div>
				<div className={styles.footer}>
					<div className={styles.footerContent}>
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
