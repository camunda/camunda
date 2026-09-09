/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {Crosshair, ZoomIn, ZoomOut} from 'lucide-react';
import {useTranslation} from 'react-i18next';

type Props = {
	onZoomReset: () => void;
	onZoomIn: () => void;
	onZoomOut: () => void;
};

const DiagramControls: React.FC<Props> = ({onZoomReset, onZoomIn, onZoomOut}) => {
	const {t} = useTranslation();
	const resetLabel = t('tasklist.taskDetailsResetDiagramZoom');
	const zoomInLabel = t('tasklist.taskDetailsZoomInDiagram');
	const zoomOutLabel = t('tasklist.taskDetailsZoomOutDiagram');

	return (
		<div className="absolute right-4 bottom-8 z-10 flex flex-col gap-1">
			<Tooltip>
				<TooltipTrigger asChild>
					<Button variant="secondary" size="icon-sm" aria-label={resetLabel} onClick={onZoomReset}>
						<Crosshair aria-hidden="true" />
					</Button>
				</TooltipTrigger>
				<TooltipContent side="left">{resetLabel}</TooltipContent>
			</Tooltip>
			<Tooltip>
				<TooltipTrigger asChild>
					<Button variant="secondary" size="icon-sm" aria-label={zoomInLabel} onClick={onZoomIn}>
						<ZoomIn aria-hidden="true" />
					</Button>
				</TooltipTrigger>
				<TooltipContent side="left">{zoomInLabel}</TooltipContent>
			</Tooltip>
			<Tooltip>
				<TooltipTrigger asChild>
					<Button variant="secondary" size="icon-sm" aria-label={zoomOutLabel} onClick={onZoomOut}>
						<ZoomOut aria-hidden="true" />
					</Button>
				</TooltipTrigger>
				<TooltipContent side="left">{zoomOutLabel}</TooltipContent>
			</Tooltip>
		</div>
	);
};

export {DiagramControls};
