/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type ReactNode, forwardRef} from 'react';
import {useTranslation} from 'react-i18next';
import {Header} from './styled';
import {Title as PanelTitle} from '../PanelTitle/styled';

type Props = {
	title?: string;
	count?: number;
	hasMoreTotalItems?: boolean;
	children?: ReactNode;
	className?: string;
	hasTopBorder?: boolean;
	size?: 'sm' | 'md';
};

const PanelHeader = forwardRef<HTMLElement, Props>(
	({title, count = 0, hasMoreTotalItems = false, children, className, size = 'md'}, ref) => {
		const {t} = useTranslation();
		return (
			<Header className={className} ref={ref} $size={size}>
				<PanelTitle>
					{title}
					{count > 0 && (
						<>
							{title === undefined ? null : <>&nbsp;&nbsp;&nbsp;-&nbsp;&nbsp;&nbsp;</>}
							{hasMoreTotalItems
								? t('operate.shared.panelHeader.resultCountMore', {count})
								: t('operate.shared.panelHeader.resultCount', {count})}
						</>
					)}
				</PanelTitle>
				{children}
			</Header>
		);
	},
);

export {PanelHeader};
