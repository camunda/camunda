/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Text} from '@camunda/design-system';
import {getBootConfig} from '#/shared/config/getBootConfig';

const Disclaimer: React.FC = () => {
	if (getBootConfig().isEnterprise) {
		return null;
	}

	return (
		<Text as="span" variant="helper" className="block pt-4 text-center">
			Non-Production License. If you would like information on production usage, please refer to our{' '}
			<Button asChild variant="link" className="h-auto p-0 align-baseline text-xs font-normal">
				<a href="https://legal.camunda.com/#self-managed-non-production-terms" target="_blank" rel="noreferrer">
					terms & conditions page
				</a>
			</Button>{' '}
			or{' '}
			<span className="whitespace-nowrap">
				<Button asChild variant="link" className="h-auto p-0 align-baseline text-xs font-normal">
					<a href="https://camunda.com/contact/" target="_blank" rel="noreferrer">
						contact sales
					</a>
				</Button>
				.
			</span>
		</Text>
	);
};

export {Disclaimer};
