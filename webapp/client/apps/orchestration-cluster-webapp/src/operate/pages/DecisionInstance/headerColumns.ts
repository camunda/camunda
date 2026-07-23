/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TFunction} from 'i18next';

type HeaderColumn = {name: string; skeletonWidth: string};

function getHeaderColumns(
	t: TFunction,
	options: {isMultiTenancyEnabled?: boolean; hasBusinessId?: boolean},
): HeaderColumn[] {
	const {isMultiTenancyEnabled = false, hasBusinessId = false} = options;
	return [
		{name: t('operate.decisionInstance.header.decisionInstanceKeyColumn'), skeletonWidth: '137px'},
		{name: t('operate.decisionInstance.header.versionColumn'), skeletonWidth: '33px'},
		...(hasBusinessId ? [{name: t('operate.decisionInstance.header.businessIdColumn'), skeletonWidth: '137px'}] : []),
		...(isMultiTenancyEnabled
			? [{name: t('operate.decisionInstance.header.tenantColumn'), skeletonWidth: '34px'}]
			: []),
		{name: t('operate.decisionInstance.header.evaluationDateColumn'), skeletonWidth: '143px'},
		{name: t('operate.decisionInstance.header.processInstanceColumn'), skeletonWidth: '137px'},
	];
}

export {getHeaderColumns};
