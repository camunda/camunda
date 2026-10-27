/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {DrdPanelContainer, DrdPanelHandle, DrdPanelSection} from './DrdPanel.styled';
import {useDrdPanelResize} from './useDrdPanelResize';

type Props = {
	children: React.ReactNode;
};

const DrdPanel: React.FC<Props> = ({children}) => {
	const {t} = useTranslation();
	const {panelRef, handleRef, width, maxWidth, minWidth} = useDrdPanelResize();

	return (
		<DrdPanelContainer>
			<DrdPanelSection
				id="operate-decision-drd-panel"
				ref={panelRef}
				aria-label={t('operate.decisionInstance.drd.panelLabel')}
			>
				{children}
			</DrdPanelSection>
			<DrdPanelHandle
				ref={handleRef}
				role="separator"
				tabIndex={0}
				aria-orientation="vertical"
				aria-label={t('operate.decisionInstance.drd.resize')}
				aria-controls="operate-decision-drd-panel"
				aria-valuemin={minWidth}
				aria-valuemax={maxWidth}
				aria-valuenow={width}
			/>
		</DrdPanelContainer>
	);
};

export {DrdPanel};
