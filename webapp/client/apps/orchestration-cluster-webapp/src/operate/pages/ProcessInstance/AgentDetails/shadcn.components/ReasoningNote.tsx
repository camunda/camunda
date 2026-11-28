/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {useTranslation} from 'react-i18next';
import {Text} from '@camunda/design-system';

const ReasoningNote: React.FC<{reasoning: string}> = ({reasoning}) => {
	const {t} = useTranslation();
	const labelId = useId();

	return (
		<section
			aria-labelledby={labelId}
			className="flex min-w-0 flex-1 flex-col gap-2 py-2 text-[var(--neutral-foreground-subtle)]"
		>
			<Text id={labelId} as="span" variant="label-sm">
				{t('operate.processInstance.details.agent.thinking')}
			</Text>
			<Text as="p" variant="body-sm" className="m-0 whitespace-pre-wrap italic break-words">
				{reasoning}
			</Text>
		</section>
	);
};

export {ReasoningNote};
