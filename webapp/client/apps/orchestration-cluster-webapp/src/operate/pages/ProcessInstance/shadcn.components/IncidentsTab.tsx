/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {useIncidentsTab} from '../useIncidentsTab';
import {IncidentsFilter} from './IncidentsFilter';
import {IncidentsTable} from './IncidentsTable';

const IncidentsTab: React.FC = () => {
	const {t} = useTranslation();
	const tab = useIncidentsTab();

	return (
		<section className="flex h-full min-h-0 flex-col" aria-label={t('operate.processInstance.incidents.list')}>
			<PanelHeader
				size="sm"
				count={tab.totalIncidentsCount}
				hasMoreTotalItems={tab.hasMoreTotalItems}
				title={t('operate.processInstance.incidents.title')}
			>
				<IncidentsFilter />
			</PanelHeader>
			<IncidentsTable
				state={tab.state}
				processInstanceKey={tab.processInstanceId}
				tenantId={tab.tenantId}
				incidents={tab.incidents}
				decisionInstancesByElementKey={tab.decisionInstancesByElementKey}
				childInstanceWithIncident={tab.childInstanceWithIncident}
				isProcessInstanceSuspended={tab.isProcessInstanceSuspended}
				hasPreviousPage={tab.hasPreviousPage}
				hasNextPage={tab.hasNextPage}
				isFetchingPreviousPage={tab.isFetchingPreviousPage}
				isFetchingNextPage={tab.isFetchingNextPage}
				onSelect={tab.selectIncident}
				onVerticalScrollStartReach={() => void tab.fetchPreviousPage()}
				onVerticalScrollEndReach={() => void tab.fetchNextPage()}
			/>
		</section>
	);
};

export {IncidentsTab};
