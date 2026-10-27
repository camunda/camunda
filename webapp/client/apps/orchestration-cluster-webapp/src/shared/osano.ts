/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getBootConfig} from '#/shared/config/getBootConfig';
import {getStage} from '#/shared/config/getStage';

function injectScript(src: string): Promise<void> {
	return new Promise((resolve) => {
		const scriptElement = document.createElement('script');

		scriptElement.src = src;
		document.head.appendChild(scriptElement);

		setTimeout(resolve, 1000);
		scriptElement.onload = () => resolve();
		scriptElement.onerror = () => resolve();
	});
}

async function loadOsano(): Promise<void> {
	const stage = getStage(window.location.host);
	const isSupported =
		!import.meta.env.DEV && ['prod', 'int', 'dev'].includes(stage) && getBootConfig().organizationId !== null;

	if (!isSupported) {
		return;
	}

	if (stage === 'dev') {
		await injectScript(import.meta.env.VITE_OSANO_DEV_ENV_URL);
	}

	if (stage === 'int') {
		await injectScript(import.meta.env.VITE_OSANO_INT_ENV_URL);
	}

	if (stage === 'prod') {
		await injectScript(import.meta.env.VITE_OSANO_PROD_ENV_URL);
	}
}

export {loadOsano};
