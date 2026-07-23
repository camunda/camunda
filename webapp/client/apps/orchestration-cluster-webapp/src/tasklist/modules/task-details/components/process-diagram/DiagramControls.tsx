/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AddIcon, CenterCircleIcon, IconButton, SubtractIcon} from '#/shared/design-system-compat';
import {useTranslation} from 'react-i18next';
import styles from './DiagramControls.module.scss';

type Props = {
	onZoomReset: () => void;
	onZoomIn: () => void;
	onZoomOut: () => void;
};

const DiagramControls: React.FC<Props> = ({onZoomReset, onZoomIn, onZoomOut}) => {
	const {t} = useTranslation();

	return (
		<div className={styles.container}>
			<IconButton
				className={styles.zoomReset}
				size="sm"
				kind="tertiary"
				align="left"
				label={t('tasklist.taskDetailsResetDiagramZoom')}
				aria-label={t('tasklist.taskDetailsResetDiagramZoom')}
				onClick={onZoomReset}
			>
				<CenterCircleIcon />
			</IconButton>
			<IconButton
				className={styles.zoomIn}
				size="sm"
				kind="tertiary"
				align="left"
				label={t('tasklist.taskDetailsZoomInDiagram')}
				aria-label={t('tasklist.taskDetailsZoomInDiagram')}
				onClick={onZoomIn}
			>
				<AddIcon />
			</IconButton>
			<IconButton
				className={styles.zoomOut}
				size="sm"
				kind="tertiary"
				align="left"
				label={t('tasklist.taskDetailsZoomOutDiagram')}
				aria-label={t('tasklist.taskDetailsZoomOutDiagram')}
				onClick={onZoomOut}
			>
				<SubtractIcon />
			</IconButton>
		</div>
	);
};

export {DiagramControls};
