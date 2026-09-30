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
import appCss from './index.scss?url';

const CarbonStylesheet = ({children}: {children: ReactNode}) => {
	const {t} = useTranslation();
	const [isLoaded, setIsLoaded] = useState(false);
	const [isError, setIsError] = useState(false);

	if (isError) {
		return (
			<main
				style={{
					minHeight: '100dvh',
					display: 'grid',
					placeItems: 'center',
					padding: '1.5rem',
					backgroundColor: 'Canvas',
					color: 'CanvasText',
					fontFamily: 'system-ui, sans-serif',
				}}
			>
				<div>
					<h1>{t('errorGenericErrorPageTitle')}</h1>
					<p>{t('errorGenericErrorPageMessage')}</p>
					<button
						type="button"
						style={{
							padding: '0.75rem 1rem',
							border: '1px solid ButtonBorder',
							backgroundColor: 'ButtonFace',
							color: 'ButtonText',
							font: 'inherit',
							cursor: 'pointer',
						}}
						onClick={() => {
							setIsLoaded(false);
							setIsError(false);
						}}
					>
						{t('errorGenericErrorPageButtonLabel')}
					</button>
				</div>
			</main>
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
