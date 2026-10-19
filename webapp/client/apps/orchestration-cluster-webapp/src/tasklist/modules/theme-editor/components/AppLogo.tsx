/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CamundaLogo} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {getAppliedTheme} from '#/tasklist/modules/theme-editor/appliedTheme';

type Props = {
	className?: string;
	/** A logo to show instead of the applied one. `null` is the Camunda logo. */
	logo?: string | null;
};

/** The logo from the applied theme in this browser, or the Camunda logo. */
const AppLogo: React.FC<Props> = ({className, logo}) => {
	const src = logo === undefined ? (getAppliedTheme()?.logo ?? null) : logo;

	if (src === null) {
		return <CamundaLogo className={className} />;
	}

	// The header gives the logo a 56px column, so a custom logo is fitted into a small box. A
	// square mark, like the Camunda one, fills it best.
	return <img src={src} alt="" className={cn('h-6 w-10 object-contain', className)} />;
};

export {AppLogo};
