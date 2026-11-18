/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Heading} from '@camunda/design-system';
import {Clock} from '@camunda/design-system/icons';
import type {ElementInstanceInspection} from '@camunda/camunda-api-zod-schemas/8.11';
import {getWaitStateStatusItems} from '#/operate/shared/utils/waitStates';

const ProcessInstanceWaitingStatus: React.FC<{waitStates: ElementInstanceInspection[]}> = ({waitStates}) => {
	const {t} = useTranslation();

	if (waitStates.length === 0) {
		return null;
	}

	return (
		<section data-testid="waiting-status" className="flex flex-col gap-2">
			<Heading as="h2" variant="heading-sm" className="m-0">
				{t('operate.processInstance.details.status')}
			</Heading>
			{getWaitStateStatusItems(waitStates).map(({key, text}) => (
				<div key={key} className="flex items-center gap-2 text-sm">
					<Clock size={16} aria-hidden />
					<span>{text}</span>
				</div>
			))}
		</section>
	);
};

export {ProcessInstanceWaitingStatus};
