/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {SVGProps} from 'react';

// Camunda logomark (the square "C" brand mark, distinct from the wordmark this
// file rendered previously). Kept in sync with the mark shipped by
// `@camunda/design-system`'s `CamundaLogo` component so Operate's Carbon login
// renders the same brand asset without depending on that package. The square
// and letter use `currentColor`/`fill="none"` plus `background`/`foreground`
// classNames so callers can theme them; the orange accent bar is hardcoded to
// the brand primary #FC5D0D, matching the DS component — it is intentionally
// not themeable, since the Camunda brand colour is fixed across all surfaces.
const SvgCamundaLogo = ({
	backgroundClassName,
	foregroundClassName,
	...props
}: SVGProps<SVGSVGElement> & {backgroundClassName?: string; foregroundClassName?: string}) => (
	<svg xmlns="http://www.w3.org/2000/svg" width={24} height={24} viewBox="0 0 24 24" fill="none" {...props}>
		<rect width={24} height={24} rx={3} className={backgroundClassName} />
		<path
			d="M11.986 15.585c1.824 0 2.762-1.075 2.776-2.967v-1.564h-1.755v1.687c0 .844-.368 1.143-.966 1.143-.585 0-.966-.3-.966-1.143V6.364c0-.843.367-1.17.966-1.156.585 0 .966.326.966 1.17v1.261h1.755V6.496c0-1.891-.94-2.965-2.762-2.965-1.823 0-2.764 1.074-2.776 2.965v6.136c0 1.864.943 2.953 2.762 2.953Z"
			className={foregroundClassName}
		/>
		<path d="M14.762 17.312H9.224v3.13h5.538v-3.13Z" fill="#FC5D0D" />
	</svg>
);
export default SvgCamundaLogo;
