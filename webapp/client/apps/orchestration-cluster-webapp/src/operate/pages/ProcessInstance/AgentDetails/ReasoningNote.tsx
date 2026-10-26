/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {useTranslation} from 'react-i18next';
import {ReasoningLabel, ReasoningRoot, ReasoningText} from './conversation.styled';

const ReasoningNote: React.FC<{reasoning: string}> = ({reasoning}) => {
	const {t} = useTranslation();
	const labelId = useId();

	return (
		<ReasoningRoot aria-labelledby={labelId}>
			<ReasoningLabel id={labelId}>{t('operate.processInstance.details.agent.thinking')}</ReasoningLabel>
			<ReasoningText>{reasoning}</ReasoningText>
		</ReasoningRoot>
	);
};

export {ReasoningNote};
