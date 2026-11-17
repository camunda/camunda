/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useForm} from 'react-final-form';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {RichTextEditorModal} from '#/operate/shared/Editors/RichTextEditorModal';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useExistingVariableEditor} from './useExistingVariableEditor';
import {variableQuery} from './variables.queries';
import {MaximizeButton} from './MaximizeButton';

type Props = {
	variableKey: string;
	variableName: string;
	variableValue: string;
	isTruncated: boolean;
};

const EditVariableModalButton: React.FC<Props> = ({variableKey, variableName, variableValue, isTruncated}) => {
	const {t} = useTranslation();
	const form = useForm();
	const {isModificationModeEnabled} = useProcessInstanceModification();
	const [isModalVisible, setIsModalVisible] = useState(false);
	const {data: variable} = useQuery({...variableQuery(variableKey), enabled: isModalVisible || isTruncated});
	const variableEditor = useExistingVariableEditor(variableName, variableValue);

	return (
		<>
			<MaximizeButton
				onClick={() => {
					if (!variableEditor.isSubmittingForm && !(isTruncated && variable === undefined)) {
						setIsModalVisible(true);
					}
				}}
			/>
			<RichTextEditorModal
				isVisible={isModalVisible}
				value={variableEditor.fieldValue ?? ''}
				variableName={variableName}
				title={t('operate.processInstance.variables.editTitle', {name: variableName})}
				onClose={() => setIsModalVisible(false)}
				onApply={(value) => {
					if (form.getState().submitting) {
						return;
					}
					form.change(variableEditor.fieldName, value);
					if (!isModificationModeEnabled) {
						void form.submit();
					}
					setIsModalVisible(false);
					const oldValue = variableEditor.getInitialValue(isTruncated ? variable?.value : undefined);
					variableEditor.stageModification({oldValue, newValue: value, isValid: true});
				}}
			/>
		</>
	);
};

export {EditVariableModalButton};
