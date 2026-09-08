/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {InstanceHeaderSkeleton} from '#/operate/shared/InstanceHeader/InstanceHeaderSkeleton';

function ProcessInstanceHeaderSkeleton() {
	const {t} = useTranslation();
	return (
		<InstanceHeaderSkeleton
			headerColumns={[
				{name: t('operate.processInstance.header.key'), skeletonWidth: '136px'},
				{name: t('operate.processInstance.header.version'), skeletonWidth: '34px'},
				{name: t('operate.processInstance.header.startDate'), skeletonWidth: '142px'},
				{name: t('operate.processInstance.header.calledInstances'), skeletonWidth: '142px'},
			]}
		/>
	);
}

export {ProcessInstanceHeaderSkeleton};
