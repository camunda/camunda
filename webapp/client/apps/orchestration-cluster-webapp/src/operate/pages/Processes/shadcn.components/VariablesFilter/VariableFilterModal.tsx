/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useLocation, useNavigate, useRouter} from '@tanstack/react-router';
import {
	Alert,
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Tabs,
	TabsContent,
	TabsList,
	TabsTrigger,
} from '@camunda/design-system';
import {Plus} from '@camunda/design-system/icons';
import {RichTextEditor} from '#/operate/shared/Editors/shadcn.components/RichTextEditor';
import type {EditorHandle} from '#/operate/shared/Editors/RichTextEditor';
import {CopyButton} from '#/operate/shared/CopyButton/shadcn.components/CopyButton';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {cn} from '#/shared/cn';
import {
	hasErrors,
	isValueRequired,
	validateCondition,
	type ConditionErrors,
	type VariableCondition,
} from '../../VariablesFilter/variableConditions';
import {
	formatConditionError,
	jsonConditionsEditorSchema,
	parseConditionsJson,
	serializeConditions,
} from '../../VariablesFilter/conditionsJsonCodec';
import {setVariableConditions, useVariableConditions} from '../../VariablesFilter/variableFilterStore';
import {truncate} from '../../VariablesFilter/truncate';
import {VariableFilterRow} from './VariableFilterRow';

const SOFT_WARNING_THRESHOLD = 8;

type Tab = 'fields' | 'json';

type FailedSubmit = {
	values: VariableCondition[];
	errors: ConditionErrors[];
};

function createDraft(): VariableCondition {
	return {name: '', operator: 'equals', value: ''};
}

function getVisibleErrors(condition: VariableCondition, index: number, failedSubmit: FailedSubmit | null) {
	const errors = failedSubmit?.errors[index];
	const submitted = failedSubmit?.values[index];
	return {
		name: submitted?.name === condition.name ? errors?.name : undefined,
		value:
			submitted?.value === condition.value && submitted.operator === condition.operator ? errors?.value : undefined,
	};
}

const VariableFilterModal: React.FC = () => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const router = useRouter();
	const isOpenedFromList = useLocation({select: ({state}) => state.operateVariableFilterOpenedFromList === true});
	const storedConditions = useVariableConditions();
	const inlineDraft = useLocation({select: ({state}) => state.operateVariableFilterDraft});
	const [conditions, setConditions] = useState<VariableCondition[]>(() =>
		inlineDraft !== undefined
			? [{...inlineDraft, operator: 'equals'}]
			: storedConditions.length > 0
				? storedConditions
				: [createDraft()],
	);
	const [failedSubmit, setFailedSubmit] = useState<FailedSubmit | null>(null);
	const [editingRowIndex, setEditingRowIndex] = useState<number | null>(null);
	const [isEditorValid, setIsEditorValid] = useState(true);
	const editorRef = useRef<EditorHandle | null>(null);
	const jsonEditorRef = useRef<EditorHandle | null>(null);
	const preEditValueRef = useRef('');
	const [tab, setTab] = useState<Tab>('fields');
	const [jsonDraft, setJsonDraft] = useState('');
	const [jsonError, setJsonError] = useState<string | null>(null);
	const [hasJsonParseWarning, setHasJsonParseWarning] = useState(false);

	useEffect(() => {
		if (isEditorValid) {
			editorRef.current?.hideMarkers();
		}
	}, [isEditorValid]);

	const updateCondition = (index: number, changes: Partial<VariableCondition>) => {
		setConditions((current) =>
			current.map((condition, position) => (position === index ? {...condition, ...changes} : condition)),
		);
	};

	const closeModal = () => {
		if (isOpenedFromList) {
			router.history.back();
		} else {
			void navigate({to: '/operate-preview/processes', search: true, replace: true});
		}
	};

	const applyConditions = (nextConditions: VariableCondition[]) => {
		setVariableConditions(
			nextConditions.map((condition) => (isValueRequired(condition.operator) ? condition : {...condition, value: ''})),
		);
		closeModal();
	};

	const handleTabChange = (nextTab: Tab) => {
		if (nextTab === 'json') {
			if (!hasJsonParseWarning) {
				setJsonDraft(serializeConditions(conditions));
			}
			setHasJsonParseWarning(false);
		} else {
			const result = parseConditionsJson(jsonDraft);
			if (result.ok) {
				setConditions(result.conditions.length > 0 ? result.conditions : [createDraft()]);
			}
			setHasJsonParseWarning(!result.ok);
		}
		setJsonError(null);
		setTab(nextTab);
	};

	const applyFromJson = () => {
		const result = parseConditionsJson(jsonDraft);
		if (!result.ok) {
			jsonEditorRef.current?.showMarkers();
			setJsonError(result.error);
			return;
		}

		const errorMessages = result.conditions.flatMap((condition, index) => {
			const errors = validateCondition(condition);
			return hasErrors(errors)
				? [formatConditionError(index, [errors.name, errors.value].filter(Boolean).join(', '))]
				: [];
		});
		if (errorMessages.length > 0) {
			setJsonError(errorMessages.join('; '));
			return;
		}

		setJsonError(null);
		applyConditions(result.conditions);
	};

	const applyFromFields = () => {
		const errors = conditions.map(validateCondition);
		if (errors.some(hasErrors)) {
			setFailedSubmit({values: conditions, errors});
			return;
		}
		applyConditions(conditions);
	};

	const cancelEditing = () => {
		if (editingRowIndex !== null) {
			updateCondition(editingRowIndex, {value: preEditValueRef.current});
		}
		setEditingRowIndex(null);
	};

	const editedCondition = editingRowIndex === null ? undefined : conditions[editingRowIndex];
	const editingVariableName = editedCondition?.name.trim();

	const submit = () => {
		if (editedCondition) {
			if (isEditorValid) {
				setEditingRowIndex(null);
			} else {
				editorRef.current?.showMarkers();
			}
		} else if (tab === 'json') {
			applyFromJson();
		} else {
			applyFromFields();
		}
	};

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					if (editedCondition) {
						cancelEditing();
					} else {
						closeModal();
					}
				}
			}}
		>
			<DialogContent size="md" dismissOnOutsideClick={false} aria-describedby={undefined}>
				<DialogHeader>
					<DialogTitle>
						{editedCondition
							? editingVariableName
								? t('operate.processes.variableFilter.editValueNamed', {name: truncate(editingVariableName)})
								: t('operate.processes.variableFilter.editValue')
							: t('operate.processes.variableFilter.heading')}
					</DialogTitle>
				</DialogHeader>
				<DialogBody className="min-h-[350px]">
					{editedCondition && editingRowIndex !== null && (
						<div className="flex flex-col gap-4">
							<div className="flex justify-end">
								<CopyButton value={editedCondition.value} />
							</div>
							<RichTextEditor
								value={editedCondition.value}
								onChange={(value) => updateCondition(editingRowIndex, {value})}
								onValidate={setIsEditorValid}
								onMount={(editor) => {
									editorRef.current = editor;
								}}
								height="45vh"
							/>
						</div>
					)}
					<div className={cn('flex flex-col gap-5', editedCondition && 'hidden')}>
						<p className="m-0 text-sm">{t('operate.processes.variableFilter.description')}</p>
						<Tabs value={tab} onValueChange={(value) => handleTabChange(value as Tab)} className="flex flex-col gap-5">
							<TabsList className="w-fit">
								<TabsTrigger value="fields">{t('operate.processes.variableFilter.fields')}</TabsTrigger>
								<TabsTrigger value="json">{t('operate.processes.variableFilter.json')}</TabsTrigger>
							</TabsList>
							<TabsContent value="fields" className="flex flex-col gap-5">
								<>
									{hasJsonParseWarning && (
										<Alert
											variant="warning"
											role="status"
											description={t('operate.processes.variableFilter.jsonWarning')}
										/>
									)}
									<div className="flex max-h-[clamp(7.5rem,calc(84vh-22rem),24rem)] min-h-0 flex-col gap-4 overflow-x-hidden overflow-y-auto">
										{conditions.map((condition, index) => (
											<VariableFilterRow
												key={`conditions[${index}]`}
												id={`conditions[${index}]`}
												condition={condition}
												errors={getVisibleErrors(condition, index, failedSubmit)}
												onChange={(changes) => updateCondition(index, changes)}
												onDelete={() => setConditions((current) => current.filter((_, position) => position !== index))}
												isDeleteHidden={conditions.length === 1}
												onEditValue={() => {
													preEditValueRef.current = condition.value;
													updateCondition(index, {value: beautifyJSON(condition.value)});
													setEditingRowIndex(index);
												}}
											/>
										))}
									</div>
									{conditions.some(({operator}) => operator === 'contains') && (
										<Alert
											variant="warning"
											role="status"
											description={t('operate.processes.variableFilter.containsWarning')}
										/>
									)}
									{conditions.length >= SOFT_WARNING_THRESHOLD && (
										<Alert
											variant="info"
											role="status"
											description={t('operate.processes.variableFilter.manyConditionsWarning')}
										/>
									)}
									<Button
										variant="ghost"
										size="sm"
										className="self-start"
										onClick={() => setConditions((current) => [...current, createDraft()])}
									>
										<Plus aria-hidden />
										{t('operate.processes.variableFilter.addCondition')}
									</Button>
								</>
							</TabsContent>
							<TabsContent value="json" className="flex flex-col gap-4">
								<>
									<div className="flex justify-end">
										<CopyButton value={jsonDraft} />
									</div>
									<div
										className={cn(
											'min-h-[280px] overflow-hidden rounded-md border',
											jsonError !== null ? 'border-destructive' : 'border-neutral-border',
										)}
									>
										<RichTextEditor
											value={jsonDraft}
											jsonSchema={jsonConditionsEditorSchema}
											isInvalid={jsonError !== null}
											options={{ariaLabel: t('operate.processes.variableFilter.json')}}
											onChange={(value) => {
												setJsonDraft(value);
												setJsonError(null);
											}}
											onMount={(editor) => {
												jsonEditorRef.current = editor;
											}}
											height="300px"
										/>
									</div>
									{jsonError !== null && (
										<Alert
											variant="destructive"
											role="alert"
											title={t('operate.processes.variableFilter.jsonError')}
											description={jsonError}
										/>
									)}
								</>
							</TabsContent>
						</Tabs>
					</div>
				</DialogBody>
				<DialogFooter>
					<Button variant="secondary" onClick={editedCondition ? cancelEditing : closeModal}>
						{t('operate.processes.variableFilter.cancel')}
					</Button>
					<Button onClick={submit}>
						{t(editedCondition ? 'operate.processes.variableFilter.save' : 'operate.processes.variableFilter.apply')}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
};

export {VariableFilterModal};
