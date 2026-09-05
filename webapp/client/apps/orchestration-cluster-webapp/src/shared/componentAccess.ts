/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentAccessDeniedError, ComponentNotAvailableError, type CamundaComponent} from '#/shared/errors';
import {getClientConfig} from '#/shared/config/getClientConfig';

function hasComponentAccess(component: CamundaComponent, authorizedComponents: string[]): boolean {
	return authorizedComponents.includes(component) || authorizedComponents.includes('*');
}

function assertComponentAccessible(component: CamundaComponent, authorizedComponents: string[]): void {
	if (!getClientConfig().components.active.includes(component)) {
		throw new ComponentNotAvailableError(component);
	}

	if (!hasComponentAccess(component, authorizedComponents)) {
		throw new ComponentAccessDeniedError(component);
	}
}

export {assertComponentAccessible, hasComponentAccess};
