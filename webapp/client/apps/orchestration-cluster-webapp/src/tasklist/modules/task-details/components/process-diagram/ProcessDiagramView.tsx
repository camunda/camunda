/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer, Tag} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {BPMNDiagram} from './BPMNDiagram';
import styles from './ProcessDiagramView.module.scss';

type Props = {
	xml: string;
	elementId: string;
	processName: string;
	processVersion: number;
};

const ProcessDiagramView: React.FC<Props> = ({xml, elementId, processName, processVersion}) => {
	const {t} = useTranslation();

	return (
		<Layer className={styles.container}>
			<div className={styles.header}>
				<span className={styles.processName}>{processName}</span>
				<Tag className={styles.version}>{t('tasklist.processViewProcessVersion', {version: processVersion})}</Tag>
			</div>
			<Layer className={styles.diagramFrame}>
				<BPMNDiagram xml={xml} highlightActivity={elementId} />
			</Layer>
		</Layer>
	);
};

export {ProcessDiagramView};
