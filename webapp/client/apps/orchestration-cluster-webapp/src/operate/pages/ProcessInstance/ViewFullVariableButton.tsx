/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {useForm} from 'react-final-form';
import {useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {RichTextEditorModal} from '#/operate/shared/Editors/RichTextEditorModal';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {variableQuery} from './variables.queries';
import {MaximizeButton} from './MaximizeButton';
import type {VariableFormValues} from './variableFormValues';
import {InlineLoading} from './variables.styled';

type Props = {
	variableKey: string;
	variableName: string;
	buttonLabel?: string;
	canEdit?: boolean;
};

const ViewFullVariableButton: React.FC<Props> = ({variableKey, variableName, buttonLabel, canEdit = false}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const form = useForm<VariableFormValues>();
	const [isLoading, setIsLoading] = useState(false);
	const [fullValue, setFullValue] = useState<string | null>(null);
	const triggerRef = useRef<HTMLButtonElement>(null);

	const open = async () => {
		if (isLoading) {
			return;
		}
		setIsLoading(true);
		try {
			setFullValue((await queryClient.fetchQuery(variableQuery(variableKey))).value);
		} catch {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('operate.shared.editors.fetchFailed', {label: variableName}),
				isDismissable: true,
			});
		} finally {
			setIsLoading(false);
		}
	};

	return (
		<>
			{isLoading && <InlineLoading data-testid="variable-operation-spinner" />}
			{buttonLabel === undefined ? (
				<MaximizeButton ref={triggerRef} isBusy={isLoading} onClick={open} />
			) : (
				<Button kind="ghost" size="sm" ref={triggerRef} aria-busy={isLoading} onClick={open}>
					{buttonLabel}
				</Button>
			)}
			{fullValue !== null && (
				<RichTextEditorModal
					value={fullValue}
					variableName={variableName}
					isVisible
					readOnly
					allowModeToggle={canEdit}
					onClose={() => {
						setFullValue(null);
						triggerRef.current?.focus();
					}}
					onApply={(value) => {
						if (form.getState().submitting) {
							return;
						}
						form.reset({name: variableName, value: fullValue, variableKey});
						form.change('value', value);
						void form.submit();
						setFullValue(null);
					}}
					title={t('operate.processInstance.variables.fullValueTitle', {name: variableName})}
					editModeTitle={t('operate.processInstance.variables.editTitle', {name: variableName})}
				/>
			)}
		</>
	);
};

export {ViewFullVariableButton};
