/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {CollapsablePanel} from '../CollapsablePanel/CollapsablePanel';
import {Button} from '@carbon/react';
import {Footer} from './styled';

type Props = {
	localStorageKey: 'isFiltersCollapsed' | 'isDecisionsFiltersCollapsed' | 'isAuditLogsFiltersCollapsed';
	children: React.ReactNode;
	onResetClick?: () => void;
	isResetButtonDisabled: boolean;
};

const FiltersPanel: React.FC<Props> = ({children, localStorageKey, onResetClick, isResetButtonDisabled}) => {
	const {t} = useTranslation();
	const storedState = getStateLocally('operate.panelStates')?.[localStorageKey];
	const isCollapsed = typeof storedState === 'boolean' ? storedState : false;
	const [panelState, setPanelState] = useState<'expanded' | 'collapsed'>(isCollapsed ? 'collapsed' : 'expanded');

	useEffect(() => {
		storeStateLocally('operate.panelStates', {
			...(getStateLocally('operate.panelStates') ?? {}),
			[localStorageKey]: panelState === 'collapsed',
		});
	}, [panelState, localStorageKey]);

	return (
		<CollapsablePanel
			label={t('operate.shared.filtersPanel.label')}
			panelPosition="LEFT"
			maxWidth={320}
			isCollapsed={panelState === 'collapsed'}
			onToggle={() => {
				setPanelState((panelState) => {
					if (panelState === 'collapsed') {
						return 'expanded';
					}

					return 'collapsed';
				});
			}}
			footer={
				<Footer>
					<Button kind="ghost" size="sm" disabled={isResetButtonDisabled} type="reset" onClick={onResetClick}>
						{t('operate.shared.filtersPanel.resetFilters')}
					</Button>
				</Footer>
			}
		>
			{children}
		</CollapsablePanel>
	);
};

export {FiltersPanel};
