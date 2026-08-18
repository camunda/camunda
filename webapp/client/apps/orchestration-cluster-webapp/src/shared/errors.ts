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

class ComponentAccessDeniedError extends Error {
	readonly component: CamundaComponent;

	constructor(component: CamundaComponent) {
		super(`Access to component "${component}" is denied`);
		this.name = 'ComponentAccessDeniedError';
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

class ProcessStartFormNotFoundError extends Error {
	constructor() {
		super('Process does not have a start form');
		this.name = 'ProcessStartFormNotFoundError';
	}
}

class ProcessStartFormImportError extends Error {
	constructor(cause: unknown) {
		super('Process start form schema could not be imported', {cause});
		this.name = 'ProcessStartFormImportError';
	}
}

export {
	type CamundaComponent,
	ComponentAccessDeniedError,
	ComponentNotAvailableError,
	EmptyProcessXmlError,
	ForbiddenError,
	ProcessStartFormImportError,
	ProcessStartFormNotFoundError,
	TruncatedVariableError,
};
