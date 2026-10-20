/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';

type Props = {
	isSuspended: boolean;
	children: React.ReactNode;
};

const SuspendedActionTooltip: React.FC<Props> = ({isSuspended, children}) => {
	const {t} = useTranslation();

	if (!isSuspended) {
		return children;
	}

	return (
		<Tooltip>
			<TooltipTrigger asChild>
				<span className="inline-flex" tabIndex={0}>
					{children}
				</span>
			</TooltipTrigger>
			<TooltipContent>{t('tasklist.taskSuspendedActionsDisabledDescription')}</TooltipContent>
		</Tooltip>
	);
};

export {SuspendedActionTooltip};
