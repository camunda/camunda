/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text} from '@camunda/design-system';
import {cn} from '#/shared/cn';

type Props = {
	message: string;
	additionalInfo?: string;
	className?: string;
};

// Carbon's heading02 (20px/28px) maps to the DS's heading-md variant size-wise,
// but heading-md defaults to font-semibold while heading02 is font-weight 400
// (same mismatch MetricPanel documents for productiveHeading04/03) — font-normal
// is forced below to match. Rendered as a paragraph (not a heading element) to
// preserve the Carbon original's semantics: EmptyMessage is embedded in panels
// and table empty states that already own their own heading structure.
const EmptyMessage: React.FC<Props> = ({message, additionalInfo, className}) => {
	return (
		<div className={cn('flex max-w-[360px] flex-col gap-2', className)}>
			<Text as="p" variant="heading-md" className="m-0 font-normal">
				{message}
			</Text>
			{additionalInfo && (
				<Text as="p" variant="body-sm" className="m-0">
					{additionalInfo}
				</Text>
			)}
		</div>
	);
};

export {EmptyMessage};
