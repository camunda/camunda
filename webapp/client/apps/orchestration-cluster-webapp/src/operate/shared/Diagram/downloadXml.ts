/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const downloadXml = ({xml, filename}: {xml: string; filename: string}): void => {
	const url = URL.createObjectURL(new Blob([xml], {type: 'application/xml'}));
	const link = document.createElement('a');
	link.href = url;
	link.download = filename;
	document.body.appendChild(link);
	try {
		link.click();
	} finally {
		link.remove();
		setTimeout(() => URL.revokeObjectURL(url), 0);
	}
};

export {downloadXml};
