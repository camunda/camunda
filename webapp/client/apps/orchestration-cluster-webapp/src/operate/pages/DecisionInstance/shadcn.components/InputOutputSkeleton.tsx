/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Skeleton} from '@camunda/design-system';

type Props = {
	columnWidths: readonly string[];
	'data-testid': string;
};

const InputOutputSkeleton: React.FC<Props> = ({columnWidths, 'data-testid': dataTestId}) => {
	return (
		<div className="relative h-full">
			<ul data-testid={dataTestId} className="absolute w-full overflow-hidden">
				{Array.from({length: 20}, (_, rowIndex) => (
					<li key={rowIndex} className="m-4 flex gap-4">
						{columnWidths.map((width, columnIndex) => (
							<Skeleton key={columnIndex} className="h-4" style={{width}} />
						))}
					</li>
				))}
			</ul>
		</div>
	);
};

export {InputOutputSkeleton};
