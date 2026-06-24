/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InlineNotification} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {FallbackProps} from 'react-error-boundary';

const ProcessesSelectErrorFallback: React.FC<FallbackProps> = () => {
	const {t} = useTranslation();

	return (
		<InlineNotification
			kind="error"
			hideCloseButton
			role="alert"
			title={t('tasklist.customFiltersModalProcessDefinitionsLoadError')}
		/>
	);
};

export {ProcessesSelectErrorFallback};
