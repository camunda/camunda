/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import type {Environment} from '#/tasklist/modules/theme-editor/presets';

type Props = {
	environment: Environment;
};

/*
 * Environment colors stay the same whatever the brand colors are, so they use the design system's
 * status colors, which a theme does not change. There is no purple status color, so stage has its own.
 */
const BADGE_STYLE = {
	dev: {variant: 'success', className: ''},
	test: {variant: 'info', className: ''},
	stage: {
		variant: 'neutral',
		className:
			'border-[#d8b4fe] bg-[#f3e8ff] text-[#6b21a8] dark:border-[#9333ea] dark:bg-[#3b0764] dark:text-[#e9d5ff]',
	},
	production: {variant: 'danger', className: ''},
} as const;

const LABEL_KEY = {
	dev: 'tasklist.themeEditorEnvironmentDev',
	test: 'tasklist.themeEditorEnvironmentTest',
	stage: 'tasklist.themeEditorEnvironmentStage',
	production: 'tasklist.themeEditorEnvironmentProduction',
} as const;

/** The tag for an environment, large enough to notice in the header. */
const EnvironmentBadge: React.FC<Props> = ({environment}) => {
	const {t} = useTranslation();
	const {variant, className} = BADGE_STYLE[environment];

	return (
		<Badge variant={variant} className={cn('h-6 px-2.5 text-sm font-semibold', className)}>
			{t(LABEL_KEY[environment])}
		</Badge>
	);
};

export {EnvironmentBadge};
