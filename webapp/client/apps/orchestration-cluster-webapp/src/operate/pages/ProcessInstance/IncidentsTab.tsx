/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {PanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {useIncidentsTab} from './useIncidentsTab';
import {IncidentsFilter} from './IncidentsFilter';
import {IncidentsTable} from './IncidentsTable';
import {Container} from './incidents.styled';

const SMOOTH_SCROLL_STEP_SIZE = 5 * 32; // five table rows

const IncidentsTab: React.FC = () => {
	const {
		processInstanceId,
		tenantId,
		isProcessInstanceSuspended,
		state,
		incidents,
		totalIncidentsCount,
		hasMoreTotalItems,
		decisionInstancesByElementKey,
		childInstanceWithIncident,
		selectIncident,
		fetchPreviousPage,
		fetchNextPage,
	} = useIncidentsTab();

	return (
		<Container>
			<PanelHeader count={totalIncidentsCount} hasMoreTotalItems={hasMoreTotalItems} size="sm">
				<IncidentsFilter />
			</PanelHeader>
			<IncidentsTable
				state={state}
				processInstanceKey={processInstanceId}
				tenantId={tenantId}
				incidents={incidents}
				decisionInstancesByElementKey={decisionInstancesByElementKey}
				childInstanceWithIncident={childInstanceWithIncident}
				isProcessInstanceSuspended={isProcessInstanceSuspended}
				onSelect={selectIncident}
				onVerticalScrollStartReach={async (scrollDown) => {
					if (await fetchPreviousPage()) {
						scrollDown(SMOOTH_SCROLL_STEP_SIZE);
					}
				}}
				onVerticalScrollEndReach={fetchNextPage}
			/>
		</Container>
	);
};

export {IncidentsTab};
