/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAppliedTheme} from '#/tasklist/modules/theme-editor/appliedTheme';
import type {Environment} from '#/tasklist/modules/theme-editor/presets';
import {EnvironmentBadge} from './EnvironmentBadge';

type Props = {
	/** An environment to show instead of the applied one. `null` shows no tag. */
	environment?: Environment | null;
};

/** The environment tag from the applied theme in this browser, if there is one. */
const AppEnvironmentTag: React.FC<Props> = ({environment}) => {
	const shown = environment === undefined ? (getAppliedTheme()?.environment ?? null) : environment;

	return shown === null ? null : <EnvironmentBadge environment={shown} />;
};

export {AppEnvironmentTag};
