/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Download, LocateFixed, Map, Maximize, Minimize, Minus, Plus} from '@camunda/design-system/icons';
import {IconButton} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';

type Props = {
	handleZoomReset: () => void;
	handleZoomIn: () => void;
	handleZoomOut: () => void;
	handleFullscreen: () => void;
	isFullscreen: boolean;
	handleMinimapToggle: () => void;
	isMinimapOpen: boolean;
	download?: {xml: string; filename: string};
};

const GROUP_CLASS_NAME = 'flex rounded-md bg-neutral-background-subtle shadow-sm';

function DiagramControls({
	handleZoomReset,
	handleZoomIn,
	handleZoomOut,
	handleFullscreen,
	isFullscreen,
	handleMinimapToggle,
	isMinimapOpen,
	download,
}: Props) {
	const {t} = useTranslation();
	const handleDownload = () => {
		if (!download) {
			return;
		}
		const url = URL.createObjectURL(new Blob([download.xml], {type: 'application/xml'}));
		const link = document.createElement('a');
		link.href = url;
		link.download = download.filename;
		document.body.appendChild(link);
		try {
			link.click();
		} finally {
			link.remove();
			setTimeout(() => URL.revokeObjectURL(url), 0);
		}
	};

	return (
		<div className="absolute right-4 bottom-10 z-[11] flex items-center gap-2">
			<div className={GROUP_CLASS_NAME}>
				<IconButton
					variant="ghost"
					size="sm"
					tooltipSide="top"
					icon={isFullscreen ? Minimize : Maximize}
					label={
						isFullscreen ? t('operate.shared.diagram.exitFullscreen') : t('operate.shared.diagram.enterFullscreen')
					}
					onClick={handleFullscreen}
				/>
				<IconButton
					variant="ghost"
					size="sm"
					tooltipSide="top"
					icon={Map}
					label={isMinimapOpen ? t('operate.shared.diagram.hideMinimap') : t('operate.shared.diagram.showMinimap')}
					className={cn('max-[640px]:hidden', isMinimapOpen && 'bg-neutral-background-strong')}
					onClick={handleMinimapToggle}
				/>
				<IconButton
					variant="ghost"
					size="sm"
					tooltipSide="top"
					icon={LocateFixed}
					label={t('operate.shared.diagram.resetZoom')}
					onClick={handleZoomReset}
				/>
				<IconButton
					variant="ghost"
					size="sm"
					tooltipSide="top"
					icon={Minus}
					label={t('operate.shared.diagram.zoomOut')}
					onClick={handleZoomOut}
				/>
				<IconButton
					variant="ghost"
					size="sm"
					tooltipSide="top"
					icon={Plus}
					label={t('operate.shared.diagram.zoomIn')}
					onClick={handleZoomIn}
				/>
			</div>
			{download && (
				<div className={GROUP_CLASS_NAME}>
					<IconButton
						variant="ghost"
						size="sm"
						tooltipSide="top"
						icon={Download}
						label={t('operate.processInstance.diagram.downloadXml')}
						onClick={handleDownload}
					/>
				</div>
			)}
		</div>
	);
}

export {DiagramControls};
