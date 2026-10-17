/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Skeleton, typographyVariants} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type Props = {
	headerColumns: {name: string; skeletonWidth: string}[];
};

const InstanceHeaderSkeleton: React.FC<Props> = ({headerColumns}) => {
	return (
		<header
			data-testid="instance-header-skeleton"
			className="flex min-w-0 items-center gap-4 border-b border-border bg-neutral-background-subtle px-4 py-1"
		>
			<Skeleton className="size-6 shrink-0 rounded-full" />
			<div className="mr-4 flex min-w-0 shrink flex-col gap-0.5">
				<Skeleton className="h-4 w-32" />
			</div>
			<table className="table-fixed border-separate border-spacing-y-0.5 text-neutral-foreground [&_td:not(:first-child)]:pl-8 [&_th:not(:first-child)]:pl-8">
				<thead>
					<tr>
						{headerColumns.map(({name}, index) => (
							<th key={index} className={cn('truncate text-left', typographyVariants({variant: 'label-sm'}))}>
								{name}
							</th>
						))}
					</tr>
				</thead>
				<tbody>
					<tr>
						{headerColumns.map(({skeletonWidth}, index) => (
							<td key={index}>
								<Skeleton className="h-4" style={{width: skeletonWidth}} />
							</td>
						))}
					</tr>
				</tbody>
			</table>
		</header>
	);
};

export {InstanceHeaderSkeleton};
