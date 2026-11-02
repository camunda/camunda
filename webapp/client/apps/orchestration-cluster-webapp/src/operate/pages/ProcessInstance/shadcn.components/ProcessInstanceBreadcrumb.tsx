/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Breadcrumb,
	BreadcrumbEllipsis,
	BreadcrumbItem,
	BreadcrumbLink,
	BreadcrumbList,
	BreadcrumbPage,
	BreadcrumbSeparator,
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuTrigger,
} from '@camunda/design-system';
import {Fragment} from 'react';
import {Link} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {CallHierarchy, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';

const PRECEDING_BREADCRUMB_COUNT = 2;
const MAX_BREADCRUMBS_VISIBLE = 4;

type Props = {
	callHierarchy: CallHierarchy[];
	processInstance: ProcessInstance;
};

const ProcessInstanceBreadcrumb: React.FC<Props> = ({callHierarchy, processInstance}) => {
	const {t} = useTranslation();
	const hasOverflow = callHierarchy.length > MAX_BREADCRUMBS_VISIBLE;
	const visibleBreadcrumbs = hasOverflow ? callHierarchy.slice(0, PRECEDING_BREADCRUMB_COUNT) : callHierarchy;
	const overflowingBreadcrumbs = hasOverflow ? callHierarchy.slice(PRECEDING_BREADCRUMB_COUNT, -1) : [];
	const lastBreadcrumb = hasOverflow ? callHierarchy.at(-1) : undefined;

	const getLinkTitle = ({processDefinitionName, processInstanceKey}: CallHierarchy) =>
		t('operate.processInstance.breadcrumb.view', {name: processDefinitionName, key: processInstanceKey});

	const renderItem = (breadcrumb: CallHierarchy) => (
		<Fragment key={breadcrumb.processInstanceKey}>
			<BreadcrumbItem>
				<BreadcrumbLink asChild>
					<Link
						to="/operate-preview/processes/$processInstanceId"
						params={{processInstanceId: breadcrumb.processInstanceKey}}
						title={getLinkTitle(breadcrumb)}
						aria-label={getLinkTitle(breadcrumb)}
					>
						{breadcrumb.processDefinitionName}
					</Link>
				</BreadcrumbLink>
			</BreadcrumbItem>
			<BreadcrumbSeparator />
		</Fragment>
	);

	return (
		<Breadcrumb className="border-b border-border px-4 py-2">
			<BreadcrumbList>
				{visibleBreadcrumbs.map(renderItem)}
				{overflowingBreadcrumbs.length > 0 && (
					<>
						<BreadcrumbItem>
							<DropdownMenu>
								<DropdownMenuTrigger aria-label={t('operate.processInstance.breadcrumb.more')}>
									<BreadcrumbEllipsis label={t('operate.processInstance.breadcrumb.more')} />
								</DropdownMenuTrigger>
								<DropdownMenuContent align="start">
									{overflowingBreadcrumbs.map((breadcrumb) => (
										<DropdownMenuItem key={breadcrumb.processInstanceKey} asChild>
											<Link
												to="/operate-preview/processes/$processInstanceId"
												params={{processInstanceId: breadcrumb.processInstanceKey}}
												title={getLinkTitle(breadcrumb)}
											>
												{breadcrumb.processDefinitionName}
											</Link>
										</DropdownMenuItem>
									))}
								</DropdownMenuContent>
							</DropdownMenu>
						</BreadcrumbItem>
						<BreadcrumbSeparator />
						{lastBreadcrumb !== undefined && renderItem(lastBreadcrumb)}
					</>
				)}
				<BreadcrumbItem>
					<BreadcrumbPage
						title={t('operate.processInstance.breadcrumb.current', {
							name: getProcessDefinitionName(processInstance),
							key: processInstance.processInstanceKey,
						})}
					>
						{getProcessDefinitionName(processInstance)}
					</BreadcrumbPage>
				</BreadcrumbItem>
			</BreadcrumbList>
		</Breadcrumb>
	);
};

export {ProcessInstanceBreadcrumb};
