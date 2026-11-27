/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {Button, Card, CardContent, toast} from '@camunda/design-system';
import {useCallback, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/components/CompleteTaskButton';
import type {CompletionStatus} from '#/tasklist/modules/task-details/useTaskCompletion';
import {CamundaFormRenderer} from '#/tasklist/modules/form-js/CamundaFormRenderer';
import type {FormManager} from '#/tasklist/modules/form-js/FormManager';
import {getFormVariables, type PartialVariable} from '#/tasklist/modules/form-js/getFormVariables';
import {useUploadDocuments} from '#/tasklist/modules/form-js/useUploadDocuments';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';
import {formatVariablesToFormData} from '#/tasklist/modules/task-details-form/formatVariablesToFormData';

type Props = {
	formSchema: string;
	variables: Variable[];
	completionStatus: CompletionStatus;
	isCompletionAllowed: boolean;
	isHidden: boolean;
	isSuspended: boolean;
	onSubmit: (variables: Record<string, unknown>) => void | Promise<void>;
	/** Shows a save button that keeps the form values without completing the task. */
	onSaveDraft?: (variables: Record<string, unknown>) => Promise<void>;
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
	isSuspended,
	onSubmit,
	onSaveDraft,
}) => {
	const {t} = useTranslation();
	const formManagerRef = useRef<FormManager | null>(null);
	const {mutateAsync: uploadDocuments} = useUploadDocuments();
	const [isImportError, setIsImportError] = useState(false);
	const [localSubmissionStatus, setLocalSubmissionStatus] = useState<CompletionStatus | null>(null);
	const [isSavingDraft, setIsSavingDraft] = useState(false);
	const formattedData = useMemo(() => formatVariablesToFormData(variables), [variables]);
	const submissionStatus = localSubmissionStatus ?? completionStatus;
	const canCompleteTask = isCompletionAllowed && !isImportError;
	const handleMount = useCallback((formManager: FormManager) => {
		formManagerRef.current = formManager;
	}, []);
	const handleSubmit = useCallback(
		async (variables: PartialVariable[]) => {
			await onSubmit(getVariablesFromSubmitPayload(variables));
		},
		[onSubmit],
	);
	const handleFileUpload = useCallback((files: Map<string, File[]>) => uploadDocuments(files), [uploadDocuments]);
	const handleImportError = useCallback(() => {
		setIsImportError(true);
		toast.error(t('tasklist.formJSInvalidSchemaErrorNotificationTitle'));
	}, [t]);
	const handleSubmitStart = useCallback(() => {
		setLocalSubmissionStatus('active');
	}, []);
	const handleSubmitReset = useCallback(() => {
		setLocalSubmissionStatus(null);
	}, []);
	const handleSubmitError = useCallback(() => {
		setLocalSubmissionStatus('error');
	}, []);
	const handleCompleteTask = useCallback(() => {
		setLocalSubmissionStatus('active');
		formManagerRef.current?.submit();
	}, []);
	const handleSaveDraft = useCallback(async () => {
		const formManager = formManagerRef.current;

		if (formManager === null || onSaveDraft === undefined) {
			return;
		}

		setIsSavingDraft(true);
		try {
			const {data, files} = formManager.getDraft();
			const variables = await getFormVariables({initialData: formattedData, data, files, handleFileUpload});
			await onSaveDraft(getVariablesFromSubmitPayload(variables));
		} catch {
			toast.error(t('tasklist.taskDetailsSaveDraftFailedMessage'));
		} finally {
			setIsSavingDraft(false);
		}
	}, [formattedData, handleFileUpload, onSaveDraft, t]);

	return (
		<div className="h-full min-h-0 w-full" data-testid="task-tab-content">
			<div className="h-full w-full" data-testid="embedded-form" tabIndex={-1}>
				<div className="flex min-h-full w-full flex-col items-center">
					<div className="w-full px-4 pb-4">
						<Card className="pt-0">
							<CardContent>
								<CamundaFormRenderer
									schema={formSchema}
									data={formattedData}
									readOnly={!canCompleteTask}
									onMount={handleMount}
									handleSubmit={handleSubmit}
									handleFileUpload={handleFileUpload}
									onImportError={handleImportError}
									onSubmitStart={handleSubmitStart}
									onSubmitSuccess={handleSubmitReset}
									onSubmitError={handleSubmitError}
									onValidationError={handleSubmitReset}
								/>
							</CardContent>
						</Card>
					</div>
					<footer className="mt-auto flex w-full justify-end gap-2 border-t border-border px-4 py-3">
						{onSaveDraft === undefined || isHidden ? null : (
							<Button
								variant="secondary"
								loading={isSavingDraft}
								disabled={!canCompleteTask || isSuspended || isSavingDraft || submissionStatus === 'active'}
								onClick={() => void handleSaveDraft()}
							>
								{t('tasklist.taskDetailsSaveDraftButtonLabel')}
							</Button>
						)}
						<CompleteTaskButton
							status={submissionStatus}
							onClick={handleCompleteTask}
							isHidden={isHidden}
							isSuspended={isSuspended}
							isDisabled={!canCompleteTask}
						/>
					</footer>
				</div>
			</div>
		</div>
	);
};

export {TaskDetailsForm};
