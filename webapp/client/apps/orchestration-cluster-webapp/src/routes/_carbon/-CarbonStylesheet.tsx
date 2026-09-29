/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, type ReactNode} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {GenericErrorPage} from '#/shared/pages/GenericErrorPage';
import appCss from './index.scss?url';

const CarbonStylesheet = ({children}: {children: ReactNode}) => {
	const {t} = useTranslation();
	const [isLoaded, setIsLoaded] = useState(false);
	const [isError, setIsError] = useState(false);

	if (isError) {
		return (
			<GenericErrorPage
				reset={() => {
					setIsLoaded(false);
					setIsError(false);
				}}
			/>
		);
	}

	return (
		<>
			{createPortal(
				<link
					rel="stylesheet"
					href={appCss}
					onLoad={() => setIsLoaded(true)}
					onError={() => {
						console.error('Failed to load the Carbon stylesheet', appCss);
						setIsError(true);
					}}
				/>,
				document.head,
			)}
			{isLoaded ? children : <div role="status">{t('appStylesLoading')}</div>}
		</>
	);
};

export {CarbonStylesheet};
