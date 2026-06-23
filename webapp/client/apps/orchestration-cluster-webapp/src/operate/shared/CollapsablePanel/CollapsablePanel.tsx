/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {forwardRef} from 'react';
import {useTranslation} from 'react-i18next';
import {Panel, Header, Collapsable, ExpandIcon, CollapseIcon, IconButton, Content} from './styled';
import {PanelTitle} from '../PanelTitle';
import {Layer} from '@carbon/react';

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
	collapsablePanelRef?: React.RefObject<HTMLDivElement | null>;
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
		const tooltipAlignment = panelPosition === 'RIGHT' ? 'left' : 'right';

		return (
			<Collapsable
				{...props}
				aria-label={label}
				$isCollapsed={isCollapsed}
				$panelPosition={panelPosition}
				$isOverlay={isOverlay}
				$maxWidth={maxWidth}
				ref={collapsablePanelRef}
			>
				{isCollapsed ? (
					<Panel data-testid="collapsed-panel" $panelPosition={panelPosition} $isClickable onClick={onToggle}>
						<IconButton
							kind="ghost"
							label={t('operate.shared.collapsablePanel.expand', {label})}
							aria-label={t('operate.shared.collapsablePanel.expand', {label})}
							align={tooltipAlignment}
							size="sm"
						>
							<ExpandIcon size={20} $panelPosition={panelPosition} />
						</IconButton>
						<PanelTitle $isVertical>{label}</PanelTitle>
					</Panel>
				) : (
					<Panel data-testid="expanded-panel" $panelPosition={panelPosition}>
						<Header $panelPosition={panelPosition}>
							<PanelTitle>{label}</PanelTitle>
							<IconButton
								kind="ghost"
								onClick={onToggle}
								label={t('operate.shared.collapsablePanel.collapse', {label})}
								aria-label={t('operate.shared.collapsablePanel.collapse', {label})}
								align={tooltipAlignment}
								size="sm"
							>
								<CollapseIcon size={20} $panelPosition={panelPosition} />
							</IconButton>
						</Header>
						<Content ref={ref} $scrollable={scrollable}>
							<Layer>{children}</Layer>
						</Content>
						{footer !== undefined && <>{footer}</>}
					</Panel>
				)}
			</Collapsable>
		);
	},
);

export {CollapsablePanel};
