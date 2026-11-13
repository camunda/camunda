/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {useDrdPanelResize} from '../useDrdPanelResize';

type Props = {
	children: React.ReactNode;
};

const DrdPanel: React.FC<Props> = ({children}) => {
	const {t} = useTranslation();
	const {panelRef, handleRef, width, maxWidth, minWidth} = useDrdPanelResize();

	return (
		<div className="absolute inset-y-0 right-0 z-10 flex">
			<section
				id="operate-decision-drd-panel"
				ref={panelRef}
				aria-label={t('operate.decisionInstance.drd.panelLabel')}
				className="h-full w-[540px] border-l border-[var(--neutral-border-subtle)] [&.resizing]:border-l-[var(--primary-action-default)]"
			>
				{children}
			</section>
			<div
				ref={handleRef}
				role="separator"
				tabIndex={0}
				aria-orientation="vertical"
				aria-label={t('operate.decisionInstance.ds.drd.resize')}
				aria-controls="operate-decision-drd-panel"
				aria-valuemin={minWidth}
				aria-valuemax={maxWidth}
				aria-valuenow={width}
				className="absolute -left-[5px] h-full w-[10px] cursor-ew-resize focus-visible:outline-2 focus-visible:outline-[var(--focus-ring)]"
			/>
		</div>
	);
};

export {DrdPanel};
