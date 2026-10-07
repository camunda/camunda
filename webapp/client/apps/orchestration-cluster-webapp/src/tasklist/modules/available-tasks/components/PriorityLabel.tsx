/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ChevronDown, ChevronUp, ChevronsUp, Equal} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {getPriorityLabel} from '#/tasklist/modules/available-tasks/getPriorityLabel';
import {LabelWithTooltip, type Align} from './LabelWithTooltip';

type Props = {
	priority: number;
	align?: Align;
};

const ICON_MAPPINGS = {
	low: ChevronDown,
	medium: Equal,
	high: ChevronUp,
	critical: ChevronsUp,
};

const PriorityLabel: React.FC<Props> = ({priority, align = 'top-end'}) => {
	const {t} = useTranslation();
	const priorityLabel = getPriorityLabel(priority);
	const PriorityIcon = ICON_MAPPINGS[priorityLabel.key];

	return (
		<LabelWithTooltip
			screenReaderText={priorityLabel.long}
			content={
				<div className="flex flex-col gap-1">
					<span className="font-medium">{t('tasklist.taskDetailsPriorityLabel')}</span>
					<span>{priorityLabel.short}</span>
				</div>
			}
			align={align}
			className="shrink-0"
		>
			<PriorityIcon className="size-4 shrink-0" data-testid={`${priorityLabel.key}-priority-icon`} aria-hidden />
			{priorityLabel.short}
		</LabelWithTooltip>
	);
};

export {PriorityLabel};
