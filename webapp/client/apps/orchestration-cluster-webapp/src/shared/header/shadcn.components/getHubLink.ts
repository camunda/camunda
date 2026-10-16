/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const MODELER_HOST_PREFIX = 'modeler.';

function getHubLink(modelerLink: string | undefined, path: string): string | undefined {
	if (modelerLink === undefined) {
		return undefined;
	}

	try {
		const url = new URL(modelerLink);
		if (!url.hostname.startsWith(MODELER_HOST_PREFIX)) {
			return undefined;
		}

		url.hostname = `hub.${url.hostname.slice(MODELER_HOST_PREFIX.length)}`;
		url.pathname = path;
		url.search = '';
		url.hash = '';
		return url.toString();
	} catch {
		return undefined;
	}
}

export {getHubLink};
