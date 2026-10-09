/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Card, CardAction, CardHeader, Skeleton} from '@camunda/design-system';

const TaskDetailsProcessSkeleton: React.FC = () => {
	return (
		<div className="flex min-h-0 w-full flex-1 p-4" data-testid="process-tab-content">
			<Card className="min-h-0 w-full flex-1 gap-0 py-0">
				<CardHeader className="border-b border-border py-4">
					<Skeleton className="h-5 w-[30%]" />
					<CardAction>
						<Skeleton className="h-5 w-20" />
					</CardAction>
				</CardHeader>
			</Card>
		</div>
	);
};

export {TaskDetailsProcessSkeleton};
