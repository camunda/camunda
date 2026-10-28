/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useEffectEvent} from 'react';
import {useLocation, useNavigate} from '@tanstack/react-router';

const useHideOptionalFiltersRequest = (onHide: () => void) => {
	const navigate = useNavigate();
	const isHideRequested = useLocation({select: ({state}) => state.operateHideOptionalFilters === true});
	const hide = useEffectEvent(onHide);

	useEffect(() => {
		if (!isHideRequested) {
			return;
		}
		hide();
		void navigate({
			to: '.',
			search: true,
			hash: true,
			state: ({operateHideOptionalFilters: _, ...state}) => state,
			replace: true,
			ignoreBlocker: true,
		});
	}, [isHideRequested, navigate]);
};

export {useHideOptionalFiltersRequest};
