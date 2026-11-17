/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type OperateClientConfig = {waitStatesEnabled: boolean};

function createOperateClientConfig(overrides?: Partial<OperateClientConfig>): OperateClientConfig {
	return {
		waitStatesEnabled: true,
		...overrides,
	};
}

function createOperateClientConfigScript(overrides?: Partial<OperateClientConfig>) {
	return `window.clientConfig = ${JSON.stringify(createOperateClientConfig(overrides))};`;
}

export {createOperateClientConfigScript};
