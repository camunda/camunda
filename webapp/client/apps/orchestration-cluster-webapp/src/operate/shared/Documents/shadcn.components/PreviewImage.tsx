/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Alert} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';

type Props = {
	src: string;
	fileName: string;
};

const PreviewImage: React.FC<Props> = ({src, fileName}) => {
	const {t} = useTranslation();
	const [hasError, setHasError] = useState(false);

	if (hasError) {
		return <Alert variant="destructive" description={t('operate.shared.documents.imagePreviewFailed', {fileName})} />;
	}

	return (
		<img
			src={src}
			alt={fileName}
			onError={() => setHasError(true)}
			className="mx-auto block max-h-[80vh] max-w-full object-contain"
		/>
	);
};

export {PreviewImage};
