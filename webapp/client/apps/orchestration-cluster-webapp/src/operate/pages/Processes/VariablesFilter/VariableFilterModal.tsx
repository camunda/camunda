/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {useLocation, useNavigate, useRouter} from '@tanstack/react-router';
import {Button, ContentSwitcher, InlineNotification, Modal, Stack, Switch} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {RichTextEditor, type EditorHandle} from '#/operate/shared/Editors/RichTextEditor';
import {CopyButton} from '#/operate/shared/CopyButton/CopyButton';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {
	hasErrors,
	isValueRequired,
	validateCondition,
	type ConditionErrors,
	type VariableCondition,
} from './variableConditions';
import {
	formatConditionError,
	jsonConditionsEditorSchema,
	parseConditionsJson,
	serializeConditions,
} from './conditionsJsonCodec';
import {setVariableConditions, useVariableConditions} from './variableFilterStore';
import {VariableFilterRow} from './VariableFilterRow';
import {truncate} from './truncate';
import {ConditionRowsScroll, Description, EditorToolbar, JsonEditorWrap, ModalContent, SwitcherWrap} from './styled';

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
			void navigate({to: '/operate/processes', search: true, replace: true});
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

	return createPortal(
		<Modal
			open
			modalLabel={editedCondition ? t('operate.processes.variableFilter.heading') : undefined}
			modalHeading={
				editedCondition
					? editingVariableName
						? t('operate.processes.variableFilter.editValueNamed', {name: truncate(editingVariableName)})
						: t('operate.processes.variableFilter.editValue')
					: t('operate.processes.variableFilter.heading')
			}
			primaryButtonText={t(
				editedCondition ? 'operate.processes.variableFilter.save' : 'operate.processes.variableFilter.apply',
			)}
			secondaryButtonText={t('operate.processes.variableFilter.cancel')}
			closeButtonLabel={t('operate.processes.variableFilter.close')}
			onRequestSubmit={() => {
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
			}}
			onRequestClose={editedCondition ? cancelEditing : closeModal}
			onSecondarySubmit={editedCondition ? cancelEditing : closeModal}
			preventCloseOnClickOutside
			size="md"
		>
			<ModalContent>
				{editedCondition && editingRowIndex !== null && (
					<Stack gap={4}>
						<EditorToolbar>
							<CopyButton value={editedCondition.value} />
						</EditorToolbar>
						<RichTextEditor
							value={editedCondition.value}
							onChange={(value) => updateCondition(editingRowIndex, {value})}
							onValidate={setIsEditorValid}
							onMount={(editor) => {
								editorRef.current = editor;
							}}
							height="45vh"
						/>
					</Stack>
				)}
				<div style={{display: editedCondition ? 'none' : undefined}}>
					<Stack gap={5}>
						<Description>{t('operate.processes.variableFilter.description')}</Description>
						<SwitcherWrap>
							<ContentSwitcher
								size="sm"
								selectedIndex={tab === 'fields' ? 0 : 1}
								onChange={({index}) => handleTabChange(index === 0 ? 'fields' : 'json')}
							>
								<Switch name="fields" text={t('operate.processes.variableFilter.fields')} />
								<Switch name="json" text={t('operate.processes.variableFilter.json')} />
							</ContentSwitcher>
						</SwitcherWrap>
						{tab === 'fields' ? (
							<>
								{hasJsonParseWarning && (
									<InlineNotification
										kind="warning"
										lowContrast
										hideCloseButton
										subtitle={t('operate.processes.variableFilter.jsonWarning')}
										role="status"
									/>
								)}
								<ConditionRowsScroll>
									<Stack gap={4}>
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
									</Stack>
								</ConditionRowsScroll>
								{conditions.some(({operator}) => operator === 'contains') && (
									<InlineNotification
										kind="warning"
										lowContrast
										hideCloseButton
										subtitle={t('operate.processes.variableFilter.containsWarning')}
										role="status"
									/>
								)}
								{conditions.length >= SOFT_WARNING_THRESHOLD && (
									<InlineNotification
										kind="info"
										lowContrast
										hideCloseButton
										subtitle={t('operate.processes.variableFilter.manyConditionsWarning')}
										role="status"
									/>
								)}
								<Button
									kind="ghost"
									size="sm"
									renderIcon={Add}
									onClick={() => setConditions((current) => [...current, createDraft()])}
								>
									{t('operate.processes.variableFilter.addCondition')}
								</Button>
							</>
						) : (
							<Stack gap={4}>
								<EditorToolbar>
									<CopyButton value={jsonDraft} />
								</EditorToolbar>
								<JsonEditorWrap $invalid={jsonError !== null}>
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
								</JsonEditorWrap>
								{jsonError !== null && (
									<InlineNotification
										kind="error"
										lowContrast
										hideCloseButton
										title={t('operate.processes.variableFilter.jsonError')}
										subtitle={jsonError}
										role="alert"
									/>
								)}
							</Stack>
						)}
					</Stack>
				</div>
			</ModalContent>
		</Modal>,
		document.getElementById('main-content') ?? document.body,
	);
};

export {VariableFilterModal};
