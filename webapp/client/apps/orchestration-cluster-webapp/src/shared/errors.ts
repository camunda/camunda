/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type CamundaComponent = 'operate' | 'tasklist' | 'admin';
class ComponentNotAvailableError extends Error {
	readonly component: CamundaComponent;

	constructor(component: CamundaComponent) {
		super(`Component "${component}" is not available`);
		this.name = 'ComponentNotAvailableError';
		this.component = component;
	}
}

class ForbiddenError extends Error {
	constructor() {
		super('Forbidden');
		this.name = 'ForbiddenError';
	}
}

class EmptyProcessXmlError extends Error {
	constructor() {
		super('Process definition XML is empty');
		this.name = 'EmptyProcessXmlError';
	}
}

class TruncatedVariableError extends Error {
	constructor(message = 'Variables are truncated') {
		super(message);
		this.name = 'TruncatedVariableError';
	}
}

export {ComponentNotAvailableError, EmptyProcessXmlError, ForbiddenError, TruncatedVariableError};
