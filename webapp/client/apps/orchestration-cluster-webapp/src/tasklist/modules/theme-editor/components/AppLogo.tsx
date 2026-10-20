/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CamundaLogo} from '@camunda/design-system';
import {useEffect, useRef} from 'react';
import {cn} from '#/shared/cn';
import {getAppliedTheme} from '#/tasklist/modules/theme-editor/appliedTheme';

type Props = {
	/** A logo to show instead of the applied one. `null` is the Camunda logo. */
	logo?: string | null;
	size?: 'header' | 'login';
	/** Reports the width the logo is shown at, so the header can make room for a wide one. */
	onWidthChange?: (width: number) => void;
};

/*
 * A custom logo is as tall as the Camunda mark and as wide as its own shape allows: never narrower
 * than a square, and never wider than the maximum, so a symbol with the company name fits too.
 */
const IMAGE_SIZE = {
	header: 'h-6 min-w-6 max-w-30',
	login: 'h-12 min-w-12 max-w-60',
};

/** The logo from the applied theme in this browser, or the Camunda logo. */
const AppLogo: React.FC<Props> = ({logo, size = 'header', onWidthChange}) => {
	const src = logo === undefined ? (getAppliedTheme()?.logo ?? null) : logo;
	const imageRef = useRef<HTMLImageElement>(null);

	useEffect(() => {
		const image = imageRef.current;

		if (onWidthChange === undefined) {
			return;
		}

		if (image === null) {
			onWidthChange(0);
			return;
		}

		const observer = new ResizeObserver(() => onWidthChange(image.getBoundingClientRect().width));
		observer.observe(image);

		return () => observer.disconnect();
	}, [src, onWidthChange]);

	if (src === null) {
		return <CamundaLogo className={size === 'login' ? 'size-12' : undefined} />;
	}

	return (
		// The box is 40px wide in the header. A narrower logo is centered, a wider one starts at the
		// left edge and overflows to the right.
		<span className={cn('flex shrink-0 items-center justify-center-safe', size === 'header' && 'h-6 w-10')}>
			<img ref={imageRef} src={src} alt="" className={cn('shrink-0 object-contain', IMAGE_SIZE[size])} />
		</span>
	);
};

export {AppLogo};
