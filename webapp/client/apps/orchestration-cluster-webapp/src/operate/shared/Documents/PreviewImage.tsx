/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {InlineNotification} from '@carbon/react';
import {PreviewImg} from './styled';

type Props = {
	src: string;
	fileName: string;
};

const PreviewImage: React.FC<Props> = ({src, fileName}) => {
	const {t} = useTranslation();
	const [hasError, setHasError] = useState(false);

	if (hasError) {
		return (
			<InlineNotification
				kind="error"
				subtitle={t('operate.shared.documents.imagePreviewFailed', {fileName})}
				hideCloseButton
				lowContrast
			/>
		);
	}

	return <PreviewImg src={src} alt={fileName} onError={() => setHasError(true)} />;
};

export {PreviewImage};
