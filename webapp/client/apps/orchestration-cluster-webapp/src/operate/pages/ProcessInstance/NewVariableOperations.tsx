/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useField, useForm} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {TrashCan} from '@carbon/react/icons';
import {RichTextEditorModal} from '#/operate/shared/Editors/RichTextEditorModal';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useNewVariableFields} from './useNewVariableFields';
import {createNewVariableFieldName} from './variableFieldNames';
import {MaximizeButton} from './MaximizeButton';
import {Operations} from './variables.styled';

type Props = {
	fieldPrefix: string;
	scopeKey: string;
	onRemove: () => void;
};

const NewVariableOperations: React.FC<Props> = ({fieldPrefix, scopeKey, onRemove}) => {
	const {t} = useTranslation();
	const form = useForm();
	const {dispatch} = useProcessInstanceModification();
	const [isModalVisible, setIsModalVisible] = useState(false);
	const valueFieldName = createNewVariableFieldName(fieldPrefix, 'value');
	const {
		input: {value: currentValue},
	} = useField<string>(valueFieldName);
	const {
		input: {value: currentId},
	} = useField<string>(createNewVariableFieldName(fieldPrefix, 'id'));
	const {currentName, isNameFieldValid, stageModification} = useNewVariableFields(fieldPrefix);

	return (
		<Operations>
			<MaximizeButton onClick={() => setIsModalVisible(true)} />
			<Button
				kind="ghost"
				size="sm"
				hasIconOnly
				renderIcon={TrashCan}
				iconDescription={t('operate.processInstance.variables.deleteDescription')}
				tooltipPosition="top"
				onClick={() => {
					onRemove();
					dispatch({
						type: 'removeVariableModification',
						scopeId: scopeKey,
						id: currentId,
						operation: 'ADD_VARIABLE',
						source: 'variables',
					});
				}}
			/>
			<RichTextEditorModal
				isVisible={isModalVisible}
				title={t('operate.processInstance.variables.newVariableTitle')}
				value={currentValue ?? ''}
				onClose={() => setIsModalVisible(false)}
				onApply={(value) => {
					form.change(valueFieldName, value);
					setIsModalVisible(false);
					stageModification({
						areFieldsValid: isNameFieldValid && Boolean(form.getFieldState(valueFieldName)?.valid),
						name: currentName,
						value,
					});
				}}
			/>
		</Operations>
	);
};

export {NewVariableOperations};
