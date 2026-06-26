/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate} from '@tanstack/react-router';
import {cn} from '#/shared/cn';
import styles from './TabListNav.module.scss';
import layoutStyles from './taskDetailsLayoutCommon.module.scss';

type TabItem = {
	key: string;
	title: string;
	label: string;
	selected: boolean;
	to: string;
	visible?: boolean;
};

type Props = {
	className?: string;
	label: string;
	items: TabItem[];
};

const TabListNav: React.FC<Props> = ({className, label, items}) => {
	const navigate = useNavigate();

	return (
		<nav className={cn(className, layoutStyles.tabs, 'cds--tabs')}>
			<div className="cds--tab--list" aria-label={label}>
				{items.map(({key, title, label: itemLabel, selected, to, visible}) => {
					const isHidden = visible === false;
					return (
						<button
							key={key}
							type="button"
							role="link"
							aria-label={itemLabel}
							aria-current={selected ? 'page' : undefined}
							className={cn({[styles.hidden!]: isHidden}, 'cds--tabs__nav-item', 'cds--tabs__nav-link', {
								'cds--tabs__nav-item--selected': selected,
							})}
							hidden={isHidden}
							aria-hidden={isHidden}
							onClick={() => navigate({to})}
						>
							<div className="cds--tabs__nav-item-label-wrapper">
								<span className="cds--tabs__nav-item-label">{title}</span>
							</div>
						</button>
					);
				})}
			</div>
		</nav>
	);
};

export {TabListNav};
export type {TabItem};
