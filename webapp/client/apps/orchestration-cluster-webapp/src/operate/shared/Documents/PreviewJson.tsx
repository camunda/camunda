/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {InlineLoading, InlineNotification} from '@carbon/react';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {RichTextEditor} from '#/operate/shared/Editors/RichTextEditor';
import {PreviewJSONContainer} from './styled';

type Props = {
	src: string;
	fileName: string;
};

const PreviewJson: React.FC<Props> = ({src, fileName}) => {
	const {t} = useTranslation();
	const {data, isPending, isError} = useQuery({
		queryKey: ['documentContent', src],
		staleTime: 'static',
		queryFn: async ({signal}) => {
			const response = await fetch(src, {credentials: 'include', headers: {Accept: 'application/json'}, signal});
			if (!response.ok) {
				throw new Error(`Failed to load document (status ${response.status})`);
			}
			return beautifyJSON(await response.text());
		},
	});
	const loading = (
		<InlineLoading
			data-testid="json-document-preview-spinner"
			description={t('operate.shared.documents.preparingJsonPreview')}
		/>
	);

	return (
		<PreviewJSONContainer>
			{isPending && loading}
			{isError && (
				<InlineNotification
					kind="error"
					subtitle={t('operate.shared.documents.jsonPreviewFailed', {fileName})}
					hideCloseButton
					lowContrast
				/>
			)}
			{data !== undefined && <RichTextEditor value={data} language="json" readOnly height="80vh" loading={loading} />}
		</PreviewJSONContainer>
	);
};

export {PreviewJson};
