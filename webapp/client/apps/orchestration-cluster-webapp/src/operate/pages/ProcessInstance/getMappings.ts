/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

type MappingType = 'Input' | 'Output';

function getMappings(businessObject: BusinessObject, type: MappingType) {
	const ioMapping = businessObject.extensionElements?.values?.find((element) => element.$type === 'zeebe:ioMapping');
	const mappingType = type === 'Input' ? 'zeebe:input' : 'zeebe:output';

	return (ioMapping?.$children ?? [])
		.filter(({$type}) => $type === mappingType)
		.map(({source, target}) => ({source, target}));
}

export {getMappings};
export type {MappingType};
