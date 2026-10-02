/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Trans, useTranslation} from 'react-i18next';
import {InlineLoading} from '@carbon/react';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {getProcessDefinitionName} from './getProcessDefinitionName';
import type {MigrationScope} from './getMigrationFilter';
import {useMigrationInstancesCount} from './useMigrationInstancesCount';

type Props = {
	source: ProcessDefinition;
	target: ProcessDefinition;
	scope: MigrationScope;
};

function MigrationDetails({source, target, scope}: Props) {
	const {t} = useTranslation();
	const instancesCount = useMigrationInstancesCount(scope.filter);

	return (
		<>
			<p>
				<Trans
					i18nKey="operate.processes.migration.details"
					values={{
						instances:
							instancesCount.status === 'success'
								? t(
										instancesCount.isCountTruncated
											? 'operate.processes.migration.truncatedInstances'
											: 'operate.processes.migration.instances',
										{count: instancesCount.count},
									)
								: t('operate.processes.migration.selectedInstances'),
					}}
					components={{
						source: (
							<strong>
								{t('operate.processes.migration.definitionVersion', {
									name: getProcessDefinitionName(source),
									version: source.version,
								})}
							</strong>
						),
						target: (
							<strong>
								{t('operate.processes.migration.definitionVersion', {
									name: getProcessDefinitionName(target),
									version: target.version,
								})}
							</strong>
						),
					}}
				/>
			</p>
			{instancesCount.status === 'pending' && <InlineLoading />}
			{instancesCount.status === 'error' && (
				<InlineLoading status="error" description={t('operate.shared.errorMessage.message')} />
			)}
		</>
	);
}

export {MigrationDetails};
