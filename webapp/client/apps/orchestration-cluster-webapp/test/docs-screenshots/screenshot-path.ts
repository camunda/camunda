/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fileURLToPath} from 'node:url';

type GetScreenshotPathOptions = {
	baseUrl: string;
	folder: string;
	fileName: string;
};

/** Resolves docs screenshot output paths so each docs page's screenshots are grouped in their own folder. */
function getScreenshotPath({baseUrl, folder, fileName}: GetScreenshotPathOptions) {
	return fileURLToPath(new URL(`./${folder}/${fileName}`, baseUrl));
}

export {getScreenshotPath};
