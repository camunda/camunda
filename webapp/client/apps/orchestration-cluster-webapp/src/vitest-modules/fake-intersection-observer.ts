/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach} from 'vitest';

class FakeIntersectionObserver implements IntersectionObserver {
	static instances: FakeIntersectionObserver[] = [];

	readonly root: Element | Document | null;
	readonly rootMargin = '';
	readonly scrollMargin = '';
	readonly thresholds = [];
	observedTargets: Element[] = [];
	private readonly callback: IntersectionObserverCallback;

	constructor(callback: IntersectionObserverCallback, options?: IntersectionObserverInit) {
		this.callback = callback;
		this.root = (options?.root as Element | Document | null | undefined) ?? null;
		FakeIntersectionObserver.instances.push(this);
	}

	observe(target: Element) {
		this.observedTargets.push(target);
	}

	unobserve() {}
	disconnect() {}
	takeRecords(): IntersectionObserverEntry[] {
		return [];
	}

	intersect(target: Element) {
		this.callback([{target, isIntersecting: true} as IntersectionObserverEntry], this);
	}

	intersectAll(targets: Element[]) {
		this.callback(
			targets.map((target) => ({target, isIntersecting: true}) as IntersectionObserverEntry),
			this,
		);
	}
}

function getObserver(): FakeIntersectionObserver {
	const observer = FakeIntersectionObserver.instances[FakeIntersectionObserver.instances.length - 1];

	if (!observer) {
		throw new Error('No IntersectionObserver was created');
	}

	return observer;
}

function setUpFakeIntersectionObserver() {
	let originalIntersectionObserver: typeof IntersectionObserver;

	beforeEach(() => {
		originalIntersectionObserver = window.IntersectionObserver;
		FakeIntersectionObserver.instances = [];
		window.IntersectionObserver = FakeIntersectionObserver as unknown as typeof IntersectionObserver;
	});

	afterEach(() => {
		window.IntersectionObserver = originalIntersectionObserver;
	});

	return {getObserver};
}

export {setUpFakeIntersectionObserver};
