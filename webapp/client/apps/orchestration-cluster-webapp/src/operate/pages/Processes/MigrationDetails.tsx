/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Trans, useTranslation} from 'react-i18next';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {getProcessDefinitionName} from './getProcessDefinitionName';
import type {MigrationScope} from './getMigrationFilter';

type Props = {
	source: ProcessDefinition;
	target: ProcessDefinition;
	scope: MigrationScope;
};

// Like legacy, this states the number of instances selected when migration was entered; it is not recounted,
// even though the request only covers the active ones.
function MigrationDetails({source, target, scope}: Props) {
	const {t} = useTranslation();

	return (
		<>
			<p>
				<Trans
					i18nKey="operate.processes.migration.details"
					values={{
						instances: t(
							scope.isCountTruncated
								? 'operate.processes.migration.truncatedInstances'
								: 'operate.processes.migration.instances',
							{count: scope.selectedCount},
						),
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
		</>
	);
}

export {MigrationDetails};
