/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BreadcrumbItem, OverflowMenu, OverflowMenuItem} from '@carbon/react';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {CallHierarchy, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';
import {ProcessInstanceLink, CarbonBreadcrumb} from './styled';

const PRECEDING_BREADCRUMB_COUNT = 2;
const MAX_BREADCRUMBS_VISIBLE = 4;

type Props = {
	callHierarchy: CallHierarchy[];
	processInstance: ProcessInstance;
};

const ProcessInstanceBreadcrumb: React.FC<Props> = ({callHierarchy, processInstance}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const hasOverflow = callHierarchy.length > MAX_BREADCRUMBS_VISIBLE;
	const visibleBreadcrumbs = hasOverflow ? callHierarchy.slice(0, PRECEDING_BREADCRUMB_COUNT) : callHierarchy;
	const overflowingBreadcrumbs = hasOverflow ? callHierarchy.slice(PRECEDING_BREADCRUMB_COUNT, -1) : [];
	const lastBreadcrumb = hasOverflow ? callHierarchy.at(-1) : undefined;

	const getLinkTitle = ({processDefinitionName, processInstanceKey}: CallHierarchy) =>
		t('operate.processInstance.breadcrumb.view', {name: processDefinitionName, key: processInstanceKey});

	const renderItem = (breadcrumb: CallHierarchy) => (
		<BreadcrumbItem key={breadcrumb.processInstanceKey}>
			<ProcessInstanceLink
				to="/operate/processes/$processInstanceId"
				params={{processInstanceId: breadcrumb.processInstanceKey}}
				title={getLinkTitle(breadcrumb)}
				aria-label={getLinkTitle(breadcrumb)}
			>
				{breadcrumb.processDefinitionName}
			</ProcessInstanceLink>
		</BreadcrumbItem>
	);

	return (
		<CarbonBreadcrumb noTrailingSlash>
			{visibleBreadcrumbs.map(renderItem)}
			{overflowingBreadcrumbs.length > 0 && (
				<>
					<BreadcrumbItem data-floating-menu-container>
						<OverflowMenu align="bottom" iconDescription={t('operate.processInstance.breadcrumb.more')}>
							{overflowingBreadcrumbs.map((breadcrumb) => (
								<OverflowMenuItem
									key={breadcrumb.processInstanceKey}
									itemText={breadcrumb.processDefinitionName}
									requireTitle
									title={getLinkTitle(breadcrumb)}
									onClick={() =>
										void navigate({
											to: '/operate/processes/$processInstanceId',
											params: {processInstanceId: breadcrumb.processInstanceKey},
										})
									}
								/>
							))}
						</OverflowMenu>
					</BreadcrumbItem>
					{lastBreadcrumb !== undefined && renderItem(lastBreadcrumb)}
				</>
			)}
			<BreadcrumbItem
				isCurrentPage
				title={t('operate.processInstance.breadcrumb.current', {
					name: getProcessDefinitionName(processInstance),
					key: processInstance.processInstanceKey,
				})}
			>
				{getProcessDefinitionName(processInstance)}
			</BreadcrumbItem>
		</CarbonBreadcrumb>
	);
};

export {ProcessInstanceBreadcrumb};
