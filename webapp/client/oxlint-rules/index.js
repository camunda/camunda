/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import apiZodSchemasVersion from './api-zod-schemas-version.js';
import noTestsInRoutes from './no-tests-in-routes.js';

export default {
	meta: {name: 'local'},
	rules: {
		'api-zod-schemas-version': apiZodSchemasVersion,
		'no-tests-in-routes': noTestsInRoutes,
	},
};
