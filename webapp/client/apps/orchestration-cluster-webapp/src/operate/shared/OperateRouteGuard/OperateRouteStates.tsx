/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ErrorComponentProps} from '@tanstack/react-router';
import {ComponentAccessDeniedError, ComponentNotAvailableError, ForbiddenError} from '#/shared/errors';
import {ComponentAccessDeniedPage} from '#/shared/pages/shadcn.components/ComponentAccessDeniedPage';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';
import {PageLayout} from '@camunda/design-system';

function OperateRouteError({error}: ErrorComponentProps) {
	if (error instanceof ComponentAccessDeniedError) {
		return (
			<PageLayout>
				<ComponentAccessDeniedPage />
			</PageLayout>
		);
	}

	if (error instanceof ComponentNotAvailableError || error instanceof ForbiddenError) {
		return (
			<PageLayout>
				<ForbiddenPage />
			</PageLayout>
		);
	}
	throw error;
}

function OperateNotFound() {
	return (
		<PageLayout>
			<NotFoundPage />
		</PageLayout>
	);
}

export {OperateRouteError, OperateNotFound};
