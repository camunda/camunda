/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge} from '@camunda/design-system';
import {File} from '@camunda/design-system/icons';
import {toHumanReadableBytes} from '#/operate/shared/Documents/humanReadableBytes';
import {middleTruncate} from '#/operate/shared/Documents/middleTruncate';
import type {DocumentParseResult} from '../parseDocumentVariable';

const DocumentValueCell: React.FC<{result: DocumentParseResult}> = ({result}) => {
	const {t} = useTranslation();
	const containerClassName = 'flex min-h-6 items-center gap-2 py-1';
	const icon = <File className="size-4 shrink-0 text-neutral-foreground-subtle" aria-hidden="true" />;

	if (result.type === 'single') {
		const {fileName, size, isExpired} = result.document;
		return (
			<div className={containerClassName}>
				{icon}
				<span title={fileName} className="min-w-0 overflow-hidden text-sm text-ellipsis whitespace-nowrap">
					{middleTruncate(fileName)}
				</span>
				<span className="shrink-0 text-xs text-neutral-foreground-subtle">{toHumanReadableBytes(size)}</span>
				{isExpired && <Badge variant="danger">{t('operate.shared.documents.expired')}</Badge>}
			</div>
		);
	}

	const count = result.documents.length;

	return (
		<div className={containerClassName}>
			{icon}
			<span className="text-sm text-neutral-foreground-subtle">
				{t(count !== 1 ? 'operate.shared.documents.countMultiple' : 'operate.shared.documents.countSingle', {
					amount: result.isLowerBound ? `${count}+` : `${count}`,
				})}
			</span>
		</div>
	);
};

export {DocumentValueCell};
