/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {typographyVariants} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type FrameProps = {
	headerTitle: string;
	isVisible?: boolean;
};

const Frame: React.FC<{frame?: FrameProps; children: React.ReactNode}> = ({frame, children}) => {
	if (frame === undefined) {
		return <>{children}</>;
	}

	const {isVisible = true, headerTitle} = frame;

	return (
		<div
			data-testid="frame-container"
			className={cn(
				'h-full',
				isVisible && 'grid grid-rows-[2rem_1fr] border-4 border-t-0 border-[var(--primary-action-default)]',
			)}
		>
			{isVisible && (
				<div
					className={cn(
						typographyVariants({variant: 'body-sm'}),
						'flex items-center bg-[var(--primary-action-default)] pl-4 font-bold text-[var(--primary-action-foreground)]',
					)}
				>
					{headerTitle}
				</div>
			)}
			{children}
		</div>
	);
};

export {Frame};
