/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Tag} from '@carbon/react';
import {Document} from '@carbon/react/icons';
import {toHumanReadableBytes} from '#/operate/shared/Documents/humanReadableBytes';
import {middleTruncate} from '#/operate/shared/Documents/middleTruncate';
import type {DocumentParseResult} from './parseDocumentVariable';
import {
	DocumentCellContainer,
	DocumentCount,
	DocumentFileName,
	DocumentIcon,
	DocumentSize,
} from './documentValueCell.styled';

const DocumentValueCell: React.FC<{result: DocumentParseResult}> = ({result}) => {
	const {t} = useTranslation();

	if (result.type === 'single') {
		const {fileName, size, isExpired} = result.document;
		return (
			<DocumentCellContainer data-testid="document-value-cell">
				<DocumentIcon>
					<Document size={16} />
				</DocumentIcon>
				<DocumentFileName title={fileName}>{middleTruncate(fileName)}</DocumentFileName>
				<DocumentSize>{toHumanReadableBytes(size)}</DocumentSize>
				{isExpired && (
					<Tag type="red" size="sm">
						{t('operate.shared.documents.expired')}
					</Tag>
				)}
			</DocumentCellContainer>
		);
	}

	const count = result.documents.length;

	return (
		<DocumentCellContainer data-testid="document-value-cell">
			<DocumentIcon>
				<Document size={16} />
			</DocumentIcon>
			<DocumentCount>
				{t(count !== 1 ? 'operate.shared.documents.countMultiple' : 'operate.shared.documents.countSingle', {
					amount: result.isLowerBound ? `${count}+` : `${count}`,
				})}
			</DocumentCount>
		</DocumentCellContainer>
	);
};

export {DocumentValueCell};
