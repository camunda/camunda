/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {forwardRef} from 'react';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {PanelTitle} from '../../PanelTitle/shadcn.components/PanelTitle';

type Props = {
	title?: string;
	count?: number;
	hasMoreTotalItems?: boolean;
	children?: React.ReactNode;
	className?: string;
	hasTopBorder?: boolean;
	size?: 'sm' | 'md';
};

const SIZE_CLASSES: Record<NonNullable<Props['size']>, string> = {
	md: 'h-12 min-h-12',
	sm: 'h-10 min-h-10',
};

const PanelHeader = forwardRef<HTMLElement, Props>(
	({title, count = 0, hasMoreTotalItems = false, children, className, size = 'md'}, ref) => {
		const {t} = useTranslation();
		return (
			<header
				ref={ref}
				className={cn(
					'flex items-center border-b border-[var(--neutral-border-subtle)] bg-[var(--neutral-background-subtle)] px-4 py-3',
					SIZE_CLASSES[size],
					className,
				)}
			>
				<PanelTitle>
					{title}
					{count > 0 && (
						<>
							{title === undefined ? null : <>&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;</>}
							{hasMoreTotalItems
								? t('operate.shared.panelHeader.resultCountMore', {count})
								: t('operate.shared.panelHeader.resultCount', {count})}
						</>
					)}
				</PanelTitle>
				{children}
			</header>
		);
	},
);

export {PanelHeader};
