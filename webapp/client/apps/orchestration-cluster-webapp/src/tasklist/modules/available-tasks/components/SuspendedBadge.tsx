/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {CirclePause} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';

type Props = {
	isIconOnly?: boolean;
};

const SuspendedBadge: React.FC<Props> = ({isIconOnly = false}) => {
	const {t} = useTranslation();

	if (!isIconOnly) {
		return (
			<Badge variant="accent" className="shrink-0">
				<CirclePause aria-hidden />
				{t('tasklist.taskSuspendedLabel')}
			</Badge>
		);
	}

	const description = t('tasklist.taskSuspendedDescription');

	return (
		<Tooltip>
			<TooltipTrigger asChild>
				<Badge variant="accent" role="img" aria-label={description} className="size-5 shrink-0 px-0">
					<CirclePause aria-hidden />
				</Badge>
			</TooltipTrigger>
			<TooltipContent side="top">{description}</TooltipContent>
		</Tooltip>
	);
};

export {SuspendedBadge};
