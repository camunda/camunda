/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SkeletonCell, SkeletonContainer, SkeletonList, SkeletonRow} from './variablesPanel.styled';

type Props = {
	columnWidths: readonly string[];
	'data-testid': string;
};

const InputOutputSkeleton: React.FC<Props> = ({columnWidths, 'data-testid': dataTestId}) => {
	return (
		<SkeletonContainer>
			<SkeletonList data-testid={dataTestId}>
				{Array.from({length: 20}, (_, index) => (
					<SkeletonRow key={index}>
						{columnWidths.map((width, columnIndex) => (
							<SkeletonCell key={columnIndex} width={width} />
						))}
					</SkeletonRow>
				))}
			</SkeletonList>
		</SkeletonContainer>
	);
};

export {InputOutputSkeleton};
