/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Dev-only preview route. Lets the migrated GenericErrorPage be viewed
// directly (no need to force a real fetch failure through the root error
// boundary). Not linked from anywhere in the app nav.
import {GenericErrorPage} from '#/shared/pages/GenericErrorPage';
import {createFileRoute} from '@tanstack/react-router';

export const Route = createFileRoute('/dev-preview/generic-error')({
	component: () => <GenericErrorPage reset={() => {}} />,
});
