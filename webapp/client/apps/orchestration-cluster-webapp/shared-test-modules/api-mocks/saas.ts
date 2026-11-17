/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const SAAS_AUDIENCE = 'cloud.dev.ultrawombat.com';
const SAAS_ORGANIZATION_ID = 'saas-organization-id';
const SAAS_CLUSTER_ID = 'saas-cluster-id';

const encodeBase64Url = (value: unknown) =>
	btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

function createSaasUserToken(): string {
	const header = encodeBase64Url({alg: 'none', typ: 'JWT'});
	const payload = encodeBase64Url({
		aud: SAAS_AUDIENCE,
		sub: 'demo',
		exp: 4102444800,
		'https://camunda.com/settings': {theme: 'system'},
	});

	return `${header}.${payload}.`;
}

type C3Organization = {
	uuid: string;
	name: string;
	permissions: {cluster: Record<string, {read: boolean}>};
};

function createC3Organization(overrides?: Partial<C3Organization>): C3Organization {
	return {
		uuid: SAAS_ORGANIZATION_ID,
		name: 'Camunda',
		permissions: {
			cluster: {
				tasklist: {read: true},
				operate: {read: true},
				admin: {read: true},
				optimize: {read: true},
			},
		},
		...overrides,
	};
}

type C3Cluster = {
	uuid: string;
	name: string;
	status: Record<string, string>;
	endpoints: Record<string, string>;
	labels: {camunda: string[]};
	generation: {name: string; versions: Record<string, string>};
};

function createC3Cluster(overrides?: Partial<C3Cluster>): C3Cluster {
	return {
		uuid: SAAS_CLUSTER_ID,
		name: 'Payments',
		status: {
			zeebe: 'Healthy',
			tasklist: 'Healthy',
			operate: 'Healthy',
			optimize: 'Healthy',
			admin: 'Healthy',
		},
		endpoints: {
			tasklist: 'https://tasklist.example.com',
			operate: 'https://operate.example.com',
			optimize: 'https://optimize.example.com',
			admin: 'https://admin.example.com',
		},
		labels: {camunda: []},
		generation: {
			name: 'Camunda 8.11',
			versions: {zeebe: '8.11.0', operate: '8.11.0', tasklist: '8.11.0', optimize: '8.11.0', connectors: '8.11.0'},
		},
		...overrides,
	};
}

export {
	SAAS_AUDIENCE,
	SAAS_CLUSTER_ID,
	SAAS_ORGANIZATION_ID,
	createC3Cluster,
	createC3Organization,
	createSaasUserToken,
};
