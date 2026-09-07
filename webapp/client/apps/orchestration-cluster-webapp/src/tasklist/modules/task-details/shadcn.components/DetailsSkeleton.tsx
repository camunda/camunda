/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Skeleton} from '@camunda/design-system';
import {cn} from '#/shared/cn';

const DetailsSkeleton: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({className, ...props}) => {
	return (
		<div
			className={cn('grid h-full grid-cols-[minmax(0,1fr)_19.5rem] overflow-hidden max-xl:grid-cols-1!', className)}
			{...props}
		>
			<div className="flex min-w-0 flex-col gap-4 p-4">
				<Skeleton className="h-5 w-48 max-w-full" />
				<Skeleton className="h-4 w-32 max-w-full" />
				<Skeleton className="h-9 w-64 max-w-full" />
			</div>
			<div className="flex flex-col gap-4 border-l border-border p-4 max-xl:hidden!">
				<Skeleton className="h-5 w-24" />
				{Array.from({length: 5}, (_, index) => (
					<div className="flex flex-col gap-2" key={index}>
						<Skeleton className="h-4 w-24" />
						<Skeleton className="h-4 w-36" />
					</div>
				))}
			</div>
		</div>
	);
};

export {DetailsSkeleton};
