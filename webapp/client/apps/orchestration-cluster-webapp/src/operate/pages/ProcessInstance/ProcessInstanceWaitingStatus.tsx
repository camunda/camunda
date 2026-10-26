/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Time} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import type {ElementInstanceInspection} from '@camunda/camunda-api-zod-schemas/8.11';
import {getWaitStateStatusItems} from '#/operate/shared/utils/waitStates';
import {SectionContainer, SectionHeading, StatusItem} from './details.styled';

const ProcessInstanceWaitingStatus: React.FC<{waitStates: ElementInstanceInspection[]}> = ({waitStates}) => {
	const {t} = useTranslation();

	if (waitStates.length === 0) {
		return null;
	}

	return (
		<SectionContainer data-testid="waiting-status">
			<SectionHeading>{t('operate.processInstance.details.status')}</SectionHeading>
			{getWaitStateStatusItems(waitStates).map(({key, text}) => (
				<StatusItem key={key}>
					<Time size={16} />
					<span>{text}</span>
				</StatusItem>
			))}
		</SectionContainer>
	);
};

export {ProcessInstanceWaitingStatus};
