/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {ForbiddenError} from '#/shared/errors';
import {DataLoadErrorAlert} from '#/operate/shared/DataLoadErrorAlert/shadcn.components/DataLoadErrorAlert';

const VariablesError: React.FC<{error: Error | null; onRetry: () => void}> = ({error, onRetry}) => {
	const {t} = useTranslation();

	return error instanceof ForbiddenError ? (
		<DataLoadErrorAlert
			title={t('operate.processInstance.variables.forbidden')}
			description={t('operate.processInstance.variables.forbiddenInfo')}
		/>
	) : (
		<DataLoadErrorAlert title={t('operate.processInstance.variables.error')} onRetry={onRetry} />
	);
};

export {VariablesError};
