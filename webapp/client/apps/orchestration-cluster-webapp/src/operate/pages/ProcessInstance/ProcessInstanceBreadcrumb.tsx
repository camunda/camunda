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
import {ProcessInstanceLink, CarbonBreadcrumb} from './styled';

function ProcessInstanceBreadcrumb({
	callHierarchy,
	processInstance,
}: {
	callHierarchy: CallHierarchy[];
	processInstance: ProcessInstance;
}) {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const hasOverflow = callHierarchy.length > 4;
	const visible = hasOverflow ? callHierarchy.slice(0, 2) : callHierarchy;
	const overflowing = hasOverflow ? callHierarchy.slice(2, -1) : [];
	const last = hasOverflow ? callHierarchy.at(-1) : undefined;
	const linkTitle = ({processDefinitionName: name, processInstanceKey: key}: CallHierarchy) =>
		t('operate.processInstance.breadcrumb.view', {name, key});
	const item = (ancestor: CallHierarchy) => (
		<BreadcrumbItem key={ancestor.processInstanceKey}>
			<ProcessInstanceLink
				to="/operate/processes/$processInstanceId"
				params={{processInstanceId: ancestor.processInstanceKey}}
				title={linkTitle(ancestor)}
			>
				{ancestor.processDefinitionName}
			</ProcessInstanceLink>
		</BreadcrumbItem>
	);
	return (
		<CarbonBreadcrumb noTrailingSlash>
			{visible.map(item)}
			{overflowing.length > 0 && (
				<BreadcrumbItem data-floating-menu-container>
					<OverflowMenu align="bottom" iconDescription={t('operate.processInstance.breadcrumb.more')}>
						{overflowing.map((ancestor) => (
							<OverflowMenuItem
								key={ancestor.processInstanceKey}
								itemText={ancestor.processDefinitionName}
								requireTitle
								title={linkTitle(ancestor)}
								onClick={() =>
									void navigate({
										to: '/operate/processes/$processInstanceId',
										params: {processInstanceId: ancestor.processInstanceKey},
									})
								}
							/>
						))}
					</OverflowMenu>
				</BreadcrumbItem>
			)}
			{last && item(last)}
			<BreadcrumbItem
				isCurrentPage
				title={t('operate.processInstance.breadcrumb.current', {
					name: processInstance.processDefinitionName,
					key: processInstance.processInstanceKey,
				})}
			>
				{processInstance.processDefinitionName}
			</BreadcrumbItem>
		</CarbonBreadcrumb>
	);
}

export {ProcessInstanceBreadcrumb};
