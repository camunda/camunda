/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import {Card, CardContent, toast} from '@camunda/design-system';
import {useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/shadcn.components/CompleteTaskButton';
import type {CompletionStatus} from '#/tasklist/modules/task-details/useTaskCompletion';
import {CamundaFormRenderer, type PartialVariable} from '#/tasklist/modules/form-js/CamundaFormRenderer';
import type {FormManager} from '#/tasklist/modules/form-js/FormManager';
import {useUploadDocuments} from '#/tasklist/modules/form-js/useUploadDocuments';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';
import {formatVariablesToFormData} from '../formatVariablesToFormData';

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

	return (
		<div className="h-full min-h-0 w-full" data-testid="task-tab-content">
			<div className="h-full w-full overflow-y-auto" data-testid="embedded-form" tabIndex={-1}>
				<div className="flex min-h-full w-full flex-col items-center">
					<div className="mt-8 w-full px-4 pb-4">
						<Card className="pt-0">
							<CardContent>
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
										toast.error(t('tasklist.formJSInvalidSchemaErrorNotificationTitle'));
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
							</CardContent>
						</Card>
					</div>
					<footer className="mt-auto flex w-full justify-end border-t border-border px-4 py-3">
						<CompleteTaskButton
							status={submissionStatus}
							onClick={() => {
								setLocalSubmissionStatus('active');
								formManagerRef.current?.submit();
							}}
							isHidden={isHidden}
							isDisabled={!canCompleteTask}
						/>
					</footer>
				</div>
			</div>
		</div>
	);
};

export {TaskDetailsForm};
