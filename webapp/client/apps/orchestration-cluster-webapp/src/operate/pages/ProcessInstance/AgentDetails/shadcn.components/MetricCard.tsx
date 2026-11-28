/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {useTranslation} from 'react-i18next';
import {IconButton, Text, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {Info} from '@camunda/design-system/icons';

type MetricCardProps = {
	title: string;
	value: number;
	valueInfo?: string;
	children?: React.ReactNode;
};

const MetricCard: React.FC<MetricCardProps> = ({title, value, valueInfo, children}) => {
	const {i18n} = useTranslation();
	const id = useId();

	return (
		<article
			aria-labelledby={id}
			className="flex min-w-[200px] flex-1 flex-col gap-3 rounded bg-[var(--neutral-background-medium)] p-5"
		>
			<Text
				id={id}
				as="span"
				variant="label-sm"
				className="uppercase tracking-wide text-[var(--neutral-foreground-subtle)]"
			>
				{title}
			</Text>
			<div className="flex items-center">
				<Text as="span" variant="heading-md" className="text-foreground">
					{value.toLocaleString(i18n.resolvedLanguage)}
				</Text>
				{valueInfo !== undefined && (
					<Tooltip>
						<TooltipTrigger asChild>
							<IconButton variant="ghost" size="sm" label={valueInfo} icon={Info} />
						</TooltipTrigger>
						<TooltipContent side="bottom">{valueInfo}</TooltipContent>
					</Tooltip>
				)}
			</div>
			{children}
		</article>
	);
};

export {MetricCard};
