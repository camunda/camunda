/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {forwardRef} from 'react';
import {Header} from './styled';
import {PanelTitle} from '../PanelTitle';
import {pluralSuffix} from '../utils/pluralSuffix';

type Props = {
	title?: string;
	count?: number;
	hasMoreTotalItems?: boolean;
	children?: React.ReactNode;
	className?: string;
	hasTopBorder?: boolean;
	size?: 'sm' | 'md';
};

const PanelHeader = forwardRef<HTMLElement, Props>(
	({title, count = 0, hasMoreTotalItems = false, children, className, size = 'md'}, ref) => {
		return (
			<Header className={className} ref={ref} $size={size}>
				<PanelTitle>
					{title}
					{count > 0 && (
						<>
							{title === undefined ? null : <>&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;</>}
							{hasMoreTotalItems ? `${count}+ results` : pluralSuffix(count, 'result')}
						</>
					)}
				</PanelTitle>
				{children}
			</Header>
		);
	},
);

export {PanelHeader};
