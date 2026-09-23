/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, EmptyState as DSEmptyState} from '@camunda/design-system';

type Props = {
	heading: string;
	description: string;
	icon: React.ReactNode;
	button?: {
		label: string;
		href?: string;
		onClick?: () => void;
	};
	link?: {
		label: string;
		href: string;
		onClick?: () => void;
	};
	className?: string;
};

const EmptyState: React.FC<Props> = ({heading, description, icon, button, link, className}) => {
	return (
		<DSEmptyState
			className={className}
			icon={icon}
			heading={heading}
			description={description}
			action={
				button === undefined ? undefined : button.href === undefined ? (
					<Button onClick={button.onClick}>{button.label}</Button>
				) : (
					<Button asChild>
						<a href={button.href} onClick={button.onClick} target="_blank" rel="noreferrer">
							{button.label}
						</a>
					</Button>
				)
			}
			secondaryAction={
				link === undefined ? undefined : (
					<Button variant="link" asChild>
						<a href={link.href} onClick={link.onClick} target="_blank" rel="noreferrer">
							{link.label}
						</a>
					</Button>
				)
			}
		/>
	);
};

export {EmptyState};
