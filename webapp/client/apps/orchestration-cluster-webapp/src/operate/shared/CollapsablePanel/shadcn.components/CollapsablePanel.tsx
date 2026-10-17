/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {forwardRef} from 'react';
import {useTranslation} from 'react-i18next';
import {Heading, IconButton} from '@camunda/design-system';
import {ChevronLeft, ChevronRight} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';

type Props = {
	label: string;
	panelPosition: 'RIGHT' | 'LEFT';
	isOverlay?: boolean;
	onToggle: () => void;
	isCollapsed: boolean;
	children?: React.ReactNode;
	footer?: React.ReactNode;
	maxWidth: number;
	scrollable?: boolean;
	collapsablePanelRef?: React.RefObject<HTMLElement | null>;
};

const CollapsablePanel = forwardRef<HTMLDivElement, Props>(
	(
		{
			label,
			panelPosition,
			maxWidth,
			isOverlay = false,
			scrollable = true,
			children,
			footer,
			isCollapsed,
			onToggle,
			collapsablePanelRef,
			...props
		},
		ref,
	) => {
		const {t} = useTranslation();
		const isLeft = panelPosition === 'LEFT';
		const tooltipSide = isLeft ? 'right' : 'left';

		return (
			<section
				{...props}
				aria-label={label}
				ref={collapsablePanelRef}
				className={cn(
					'h-full',
					isCollapsed && 'min-w-12',
					isOverlay && cn('absolute z-[7999]', isLeft ? 'left-0' : 'right-0'),
				)}
				style={isCollapsed ? undefined : {width: maxWidth, minWidth: maxWidth}}
			>
				{isCollapsed ? (
					<div
						data-testid="collapsed-panel"
						onClick={onToggle}
						className={cn(
							'flex h-full cursor-pointer flex-col items-center gap-6 bg-background p-2',
							isLeft ? 'border-r border-border' : 'border-l border-border',
						)}
					>
						<IconButton
							variant="ghost"
							size="sm"
							label={t('operate.shared.collapsablePanel.expand', {label})}
							tooltipSide={tooltipSide}
							icon={isLeft ? ChevronRight : ChevronLeft}
						/>
						<Heading as="h2" variant="heading-xs" className="m-0 text-foreground [writing-mode:vertical-lr] rotate-180">
							{label}
						</Heading>
					</div>
				) : (
					<div
						data-testid="expanded-panel"
						className={cn(
							'flex h-full flex-col bg-background',
							isLeft ? 'border-r border-border' : 'border-l border-border',
						)}
					>
						<header
							className={cn(
								'flex h-12 min-h-12 items-center justify-between border-b border-border bg-background px-4 py-3',
								!isLeft && 'flex-row-reverse justify-end gap-6',
							)}
						>
							<Heading as="h2" variant="heading-xs" className="m-0 text-foreground">
								{label}
							</Heading>
							<IconButton
								variant="ghost"
								size="sm"
								onClick={onToggle}
								label={t('operate.shared.collapsablePanel.collapse', {label})}
								tooltipSide={tooltipSide}
								icon={isLeft ? ChevronLeft : ChevronRight}
							/>
						</header>
						<div ref={ref} className={cn('relative grow', scrollable ? 'overflow-auto' : 'overflow-hidden')}>
							{children}
						</div>
						{footer !== undefined && <>{footer}</>}
					</div>
				)}
			</section>
		);
	},
);

export {CollapsablePanel};
