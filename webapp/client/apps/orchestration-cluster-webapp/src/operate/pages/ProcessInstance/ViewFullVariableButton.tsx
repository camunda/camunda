/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {Maximize} from '@carbon/react/icons';
import {RichTextEditorModal} from '#/operate/shared/Editors/RichTextEditorModal';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {variableQuery} from './variables.queries';
import {InlineLoading} from './variables.styled';

type Props = {
	variableKey: string;
	variableName: string;
	buttonLabel?: string;
};

const ViewFullVariableButton: React.FC<Props> = ({variableKey, variableName, buttonLabel}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const [isLoading, setIsLoading] = useState(false);
	const [fullValue, setFullValue] = useState<string | null>(null);

	const open = async () => {
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

	if (isLoading) {
		return <InlineLoading data-testid="variable-operation-spinner" />;
	}

	return (
		<>
			{buttonLabel === undefined ? (
				<Button
					kind="ghost"
					hasIconOnly
					renderIcon={Maximize}
					size="sm"
					aria-label={t('operate.processInstance.variables.open')}
					iconDescription={t('operate.processInstance.variables.openDescription')}
					tooltipPosition="top"
					onClick={open}
				/>
			) : (
				<Button kind="ghost" size="sm" onClick={open}>
					{buttonLabel}
				</Button>
			)}
			{fullValue !== null && (
				<RichTextEditorModal
					value={fullValue}
					variableName={variableName}
					isVisible
					readOnly
					onClose={() => setFullValue(null)}
					title={t('operate.processInstance.variables.fullValueTitle', {name: variableName})}
				/>
			)}
		</>
	);
};

export {ViewFullVariableButton};
