/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';

type DrdPanelState = 'closed' | 'minimized' | 'maximized';

function useDrdPanelState() {
	const [state, setState] = useState<DrdPanelState>(() => {
		const storedState = getStateLocally('operate.panelStates')?.drdPanelState;
		return storedState === 'closed' || storedState === 'maximized' ? storedState : 'minimized';
	});

	function update(state: DrdPanelState) {
		storeStateLocally('operate.panelStates', {
			...(getStateLocally('operate.panelStates') ?? {}),
			drdPanelState: state,
		});
		setState(state);
	}

	return [state, update] as const;
}

function getDrdPanelWidth() {
	const width = getStateLocally('operate.panelStates')?.drdPanelWidth;
	return typeof width === 'number' && Number.isFinite(width) ? width : null;
}

function persistDrdPanelWidth(width: number) {
	storeStateLocally('operate.panelStates', {
		...(getStateLocally('operate.panelStates') ?? {}),
		drdPanelWidth: width,
	});
}

export {useDrdPanelState, getDrdPanelWidth, persistDrdPanelWidth};
export type {DrdPanelState};
