/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text} from '@camunda/design-system';
import {Link} from '@camunda/design-system/carbon-compat';
import {getBootConfig} from '#/shared/config/getBootConfig';
import styles from './styles.module.scss';

const Disclaimer: React.FC = () => {
	if (getBootConfig().isEnterprise) {
		return null;
	}

	return (
		<Text as="span" variant="helper" className={styles.container}>
			Non-Production License. If you would like information on production usage, please refer to our{' '}
			<Link href="https://legal.camunda.com/#self-managed-non-production-terms" target="_blank">
				terms & conditions page
			</Link>{' '}
			or{' '}
			<Link href="https://camunda.com/contact/" target="_blank">
				contact sales
			</Link>
			.
		</Text>
	);
};

export {Disclaimer};
