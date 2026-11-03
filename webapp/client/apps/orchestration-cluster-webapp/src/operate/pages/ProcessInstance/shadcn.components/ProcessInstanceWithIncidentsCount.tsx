/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstance} from './ProcessInstance';
import {useVisibleProcessInstanceTabs} from './useVisibleProcessInstanceTabs';

type Props = Omit<React.ComponentProps<typeof ProcessInstance>, 'visibleTabs' | 'incidentsCount'>;

const ProcessInstanceWithIncidentsCount: React.FC<Props> = ({children, isDesktop = true, ...props}) => {
	const {visibleTabs, incidentsCount, isActiveTabHidden} = useVisibleProcessInstanceTabs(props.activeTab, isDesktop);

	return (
		<ProcessInstance {...props} isDesktop={isDesktop} visibleTabs={visibleTabs} incidentsCount={incidentsCount}>
			{!isActiveTabHidden && children}
		</ProcessInstance>
	);
};

export {ProcessInstanceWithIncidentsCount};
