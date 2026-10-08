/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createElement, forwardRef} from 'react';
import {useTranslation} from 'react-i18next';
import {Heading, IconButton} from '@camunda/design-system';
import {ChevronLeft, ChevronRight} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';

const COLLAPSED_WIDTH = 56;

type Props = {
	label: string;
	icon?: React.ComponentProps<typeof IconButton>['icon'];
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
			icon,
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
		const hasIcon = icon !== undefined;
		const toggleChevron = isLeft === isCollapsed ? ChevronRight : ChevronLeft;

		return (
			<section
				{...props}
				aria-label={label}
				ref={collapsablePanelRef}
				data-testid={isCollapsed ? 'collapsed-panel' : 'expanded-panel'}
				className={cn(
					'h-full shrink-0 overflow-hidden bg-background transition-[width] duration-150 ease-out motion-reduce:transition-none',
					isLeft ? 'border-r border-border' : 'border-l border-border',
					isOverlay && cn('absolute z-[7999]', isLeft ? 'left-0' : 'right-0'),
				)}
				style={{width: isCollapsed ? COLLAPSED_WIDTH : maxWidth}}
			>
				<div className="flex h-full flex-col" style={{width: maxWidth}}>
					<header
						className={cn(
							'flex h-12 items-center border-b border-border bg-background',
							isCollapsed || hasIcon ? 'px-3' : 'px-4',
							!isLeft && 'flex-row-reverse justify-end gap-6',
						)}
					>
						{isCollapsed ? (
							<IconButton
								variant="ghost"
								size="sm"
								onClick={onToggle}
								label={t('operate.shared.collapsablePanel.expand', {label})}
								tooltipSide={tooltipSide}
								icon={icon ?? toggleChevron}
							/>
						) : (
							icon && (
								<span className="flex size-8 shrink-0 items-center justify-center">
									{createElement(icon, {'aria-hidden': true, className: 'size-4'})}
								</span>
							)
						)}
						<Heading
							as="h2"
							variant="heading-xs"
							className={cn('m-0 min-w-0 flex-1 truncate text-foreground', hasIcon && 'ml-2', isCollapsed && 'sr-only')}
						>
							{label}
						</Heading>
						{!isCollapsed && (
							<IconButton
								variant="ghost"
								size="sm"
								onClick={onToggle}
								label={t('operate.shared.collapsablePanel.collapse', {label})}
								tooltipSide={tooltipSide}
								icon={toggleChevron}
							/>
						)}
					</header>
					<div className={cn('flex min-h-0 grow flex-col', isCollapsed && 'invisible')} inert={isCollapsed}>
						<div ref={ref} className={cn('relative grow', scrollable ? 'overflow-auto' : 'overflow-hidden')}>
							{children}
						</div>
						{footer !== undefined && <div className="border-t border-border p-2">{footer}</div>}
					</div>
				</div>
			</section>
		);
	},
);

export {CollapsablePanel};
