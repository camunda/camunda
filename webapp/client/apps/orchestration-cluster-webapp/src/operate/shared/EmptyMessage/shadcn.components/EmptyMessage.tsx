/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Heading, Text} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type Props = {
	message: string;
	additionalInfo?: string;
	className?: string;
};

// Carbon's heading02 (20px/28px) maps to the DS's heading-md variant size-wise,
// but heading-md defaults to font-semibold while heading02 is font-weight 400
// (same mismatch MetricPanel documents for productiveHeading04/03) — font-normal
// is forced below to match.
const EmptyMessage: React.FC<Props> = ({message, additionalInfo, className}) => {
	return (
		<div className={cn('flex max-w-[360px] flex-col gap-2', className)}>
			<Heading as="h2" variant="heading-md" className="m-0 font-normal">
				{message}
			</Heading>
			{additionalInfo && (
				<Text as="p" variant="body-sm" className="m-0">
					{additionalInfo}
				</Text>
			)}
		</div>
	);
};

export {EmptyMessage};
