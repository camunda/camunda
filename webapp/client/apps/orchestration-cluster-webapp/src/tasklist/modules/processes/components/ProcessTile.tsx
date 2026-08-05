/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {Button, Stack, Tag} from '@carbon/react';
import {ArrowRight, List} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import styles from './ProcessTile.module.scss';

type Props = {
	process: ProcessDefinition;
};

function ProcessTile({process}: Props) {
	const {t} = useTranslation();
	const displayName = process.name ?? process.processDefinitionId;

	return (
		<div className={styles.container}>
			<Stack className={styles.content}>
				<Stack className={styles.titleWrapper}>
					<div className={styles.titleRow}>
						<h2 className={styles.title} title={displayName}>
							{displayName}
						</h2>
					</div>
					<span className={styles.subtitle} title={process.processDefinitionId}>
						{displayName === process.processDefinitionId ? '' : process.processDefinitionId}
					</span>
				</Stack>
				<div className={styles.buttonRow}>
					<ul
						className={styles.attributes}
						title={t('tasklist.processesProcessTileAttributes')}
						aria-hidden={!process.hasStartForm}
					>
						{process.hasStartForm ? (
							<li>
								<Tag renderIcon={List}>{t('tasklist.processesProcessTileAttributeRequiresForm')}</Tag>
							</li>
						) : null}
					</ul>
					<Button type="button" kind="tertiary" size="sm" renderIcon={process.hasStartForm ? ArrowRight : undefined}>
						{t('tasklist.processesTileStartProcessButtonLabel')}
					</Button>
				</div>
			</Stack>
		</div>
	);
}

export {ProcessTile};
