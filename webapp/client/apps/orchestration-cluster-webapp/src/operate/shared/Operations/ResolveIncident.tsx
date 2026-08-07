/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {OperationItem} from '#/operate/shared/OperationItem/OperationItem';

type Props = {
	processInstanceKey: ProcessInstance['processInstanceKey'];
	onExecute: () => void;
	disabled?: boolean;
};

const ResolveIncident: React.FC<Props> = ({processInstanceKey, onExecute, disabled = false}) => {
	const {t} = useTranslation();

	return (
		<OperationItem
			type="RESOLVE_INCIDENT"
			onClick={onExecute}
			title={t('operate.shared.operations.retryTitle', {processInstanceKey})}
			disabled={disabled}
			size="sm"
		/>
	);
};

export {ResolveIncident};
