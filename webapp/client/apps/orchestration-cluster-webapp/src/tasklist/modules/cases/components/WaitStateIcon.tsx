/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Braces, Cog, Mail, Radio, Sparkles, Timer, TriangleAlert, UserRound} from '@camunda/design-system/icons';
import type {WaitStateKind} from '#/tasklist/modules/cases/getWaitStateLabel';
import {cn} from '#/shared/cn';

const ICON_MAPPINGS = {
	userTask: UserRound,
	message: Mail,
	timer: Timer,
	signal: Radio,
	condition: Braces,
	job: Cog,
	listener: Cog,
	adHoc: Sparkles,
	stuck: TriangleAlert,
} satisfies Record<WaitStateKind, unknown>;

const ICON_COLOR_MAPPINGS = {
	userTask: 'text-[color:var(--info-action-default)]',
	message: 'text-[color:var(--warning-action-default)]',
	timer: 'text-neutral-foreground-subtle',
	signal: 'text-[color:var(--warning-action-default)]',
	condition: 'text-[color:var(--warning-action-default)]',
	job: 'text-neutral-foreground-subtle',
	listener: 'text-neutral-foreground-subtle',
	adHoc: 'text-[color:var(--accent-action-default)]',
	stuck: 'text-[color:var(--danger-action-default)]',
} as const satisfies Record<WaitStateKind, string>;

type Props = {
	kind: WaitStateKind;
	isColored?: boolean;
	className?: string;
	label?: string;
};

const WaitStateIcon: React.FC<Props> = ({kind, isColored = true, className, label}) => {
	const Icon = ICON_MAPPINGS[kind];

	return (
		<Icon
			className={cn('size-4 shrink-0', isColored ? ICON_COLOR_MAPPINGS[kind] : undefined, className)}
			aria-label={label}
			aria-hidden={label === undefined ? true : undefined}
		/>
	);
};

export {WaitStateIcon};
