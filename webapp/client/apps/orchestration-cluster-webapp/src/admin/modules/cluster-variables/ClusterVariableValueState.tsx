/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LoaderCircle} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';

type Props = {
	status: 'pending' | 'error';
};

const ClusterVariableValueState: React.FC<Props> = ({status}) => {
	const {t} = useTranslation();

	return status === 'pending' ? (
		<div role="status" className="flex items-center gap-2 p-2 text-sm text-muted-foreground">
			<LoaderCircle className="size-4 animate-spin" aria-hidden />
			{t('admin.clusterVariables.valueLoading')}
		</div>
	) : (
		<p role="alert" className="p-2 text-sm text-danger-foreground-subtle">
			{t('admin.clusterVariables.valueLoadError')}
		</p>
	);
};

export {ClusterVariableValueState};
