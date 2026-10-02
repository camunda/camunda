/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';

function getElementName({businessObjects, elementId}: {businessObjects?: BusinessObjects; elementId?: string}) {
	const name = (elementId ? businessObjects?.[elementId] : undefined)?.name;
	return name?.trim() ? name : (elementId ?? '');
}

export {getElementName};
