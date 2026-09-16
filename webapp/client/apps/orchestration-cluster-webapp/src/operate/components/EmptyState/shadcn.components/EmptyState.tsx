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

// Carbon's EmptyState (button/link props) mapped onto the DS EmptyState's
// action/secondaryAction slots — see docs/migration/operate-dashboard-tiering.md.
// `EmptyState` here is native DS, not the carbon-compat shim (there isn't one: DS
// EmptyState's API already diverges enough from Carbon's ad hoc wrapper that this is a
// REMAP, same tier as camunda-composite-components' `C3EmptyState`).
const EmptyState: React.FC<Props> = ({heading, description, icon, button, link, className}) => {
	return (
		<DSEmptyState
			className={className}
			icon={icon}
			heading={heading}
			description={description}
			action={
				button === undefined ? undefined : (
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
