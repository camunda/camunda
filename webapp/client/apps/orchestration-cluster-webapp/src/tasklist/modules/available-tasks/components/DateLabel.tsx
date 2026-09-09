/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';
import {LabelWithTooltip, type Align} from './LabelWithTooltip';

type Props = {
	date: Exclude<ReturnType<typeof formatISODateTime>, null>;
	relativeLabel: string;
	absoluteLabel: string;
	icon?: React.ReactNode;
	align?: Align;
};

const DateLabel: React.FC<Props> = ({date, relativeLabel, absoluteLabel, icon, align = 'top-start'}) => (
	<LabelWithTooltip
		title={
			['week', 'months', 'years'].includes(date.relative.resolution)
				? `${absoluteLabel} ${date.relative.speech}`
				: `${relativeLabel} ${date.relative.speech}`
		}
		content={
			<div className="flex flex-col gap-1">
				<span className="font-medium">{absoluteLabel}</span>
				<span>{date.absolute.text}</span>
			</div>
		}
		align={align}
	>
		{icon}
		{date.relative.text}
	</LabelWithTooltip>
);

export {DateLabel};
