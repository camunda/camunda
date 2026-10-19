/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* global process, Buffer */

// Deploys the demo case models and starts loan cases in different stages.
// Usage: node seed.mjs [--deploy-only]
// Environment: CAMUNDA_URL (default http://localhost:8080), CAMUNDA_USER / CAMUNDA_PASSWORD (default demo / demo)

import {readFile, readdir} from 'node:fs/promises';
import {basename, dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const BASE_URL = `${process.env.CAMUNDA_URL ?? 'http://localhost:8080'}/v2`;
const AUTHORIZATION = `Basic ${Buffer.from(`${process.env.CAMUNDA_USER ?? 'demo'}:${process.env.CAMUNDA_PASSWORD ?? 'demo'}`).toString('base64')}`;
const DIRECTORY = dirname(fileURLToPath(import.meta.url));
const RUN_ID = Date.now().toString(36).slice(-4).toUpperCase();

async function api(method, path, body) {
	const isForm = body instanceof FormData;
	const response = await fetch(`${BASE_URL}${path}`, {
		method,
		headers: {
			authorization: AUTHORIZATION,
			...(body === undefined || isForm ? {} : {'content-type': 'application/json'}),
		},
		body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
	});
	const text = await response.text();

	if (!response.ok) {
		throw new Error(`${method} ${path} failed with ${response.status}: ${text}`);
	}

	return text === '' ? null : JSON.parse(text);
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function deploy() {
	const files = [
		...(await readdir(DIRECTORY)).filter((file) => file.endsWith('.bpmn')).map((file) => join(DIRECTORY, file)),
		...(await readdir(join(DIRECTORY, 'forms'))).map((file) => join(DIRECTORY, 'forms', file)),
	];
	const form = new FormData();

	for (const file of files) {
		form.append('resources', new Blob([await readFile(file)]), basename(file));
	}

	const {deployments} = await api('POST', '/deployments', form);
	console.log(`Deployed ${deployments.length} resources`);
}

async function uploadDocument(fileName, content) {
	const form = new FormData();
	form.append('file', new Blob([content], {type: 'text/plain'}), fileName);

	try {
		return await api('POST', '/documents', form);
	} catch (error) {
		console.warn(`Skipping document ${fileName}: ${error.message}`);
		return null;
	}
}

/** Waits until the case has an open user task for the element, then completes it. */
async function completeTask(processInstanceKey, elementId, variables = {}) {
	for (let attempt = 0; attempt < 30; attempt++) {
		const {items} = await api('POST', '/user-tasks/search', {
			filter: {processInstanceKey, elementId, state: 'CREATED'},
		});

		if (items.length > 0) {
			await api('POST', `/user-tasks/${items[0].userTaskKey}/completion`, {variables});
			return;
		}

		await sleep(500);
	}

	throw new Error(`No open task ${elementId} in case ${processInstanceKey}`);
}

async function waitForElement(processInstanceKey, elementId) {
	for (let attempt = 0; attempt < 30; attempt++) {
		const {items} = await api('POST', '/element-instances/search', {
			filter: {processInstanceKey, elementId, state: 'ACTIVE'},
		});

		if (items.length > 0) {
			return items[0];
		}

		await sleep(500);
	}

	throw new Error(`Element ${elementId} is not active in case ${processInstanceKey}`);
}

async function startLoan({caseId, applicantName, loanAmount, termYears, loanPurpose, monthlyIncome, documents}) {
	const {processInstanceKey} = await api('POST', '/process-instances', {
		processDefinitionId: 'loan-application',
		businessId: caseId,
		variables: {
			caseId,
			loanTitle: `Loan for ${applicantName} — ${loanAmount.toLocaleString('en')} EUR`,
			applicantName,
			applicantEmail: `${applicantName.toLowerCase().replace(/\W+/g, '.')}@example.com`,
			loanAmount,
			termYears,
			loanPurpose,
			monthlyIncome,
			loanOfficer: 'demo',
			documents,
		},
	});
	console.log(`Started ${caseId} (${processInstanceKey})`);
	return processInstanceKey;
}

async function main() {
	await deploy();

	if (process.argv.includes('--deploy-only')) {
		return;
	}

	const documents = (
		await Promise.all([
			uploadDocument('income-proof.txt', 'Income proof: 4,200 EUR per month'),
			uploadDocument('passport.txt', 'Passport scan'),
		])
	).filter((document) => document !== null);

	// 1. A new case: the first stage waits for a loan officer
	await startLoan({
		caseId: `LA-${RUN_ID}-01`,
		applicantName: 'Jane Doe',
		loanAmount: 25000,
		termYears: 5,
		loanPurpose: 'Car',
		monthlyIncome: 4200,
		documents,
	});

	// 2. A case in the ad-hoc stage "Credit assessment"
	const creditCase = await startLoan({
		caseId: `LA-${RUN_ID}-02`,
		applicantName: 'Sara Connor',
		loanAmount: 365000,
		termYears: 30,
		loanPurpose: 'Home',
		monthlyIncome: 9800,
		documents,
	});
	await completeTask(creditCase, 'review-application', {applicationComplete: true});
	await completeTask(creditCase, 'check-documents', {identityVerified: true, incomeVerified: true});

	// 3. A case past "Credit assessment": Done closed the stage
	const underwriterCase = await startLoan({
		caseId: `LA-${RUN_ID}-03`,
		applicantName: 'Marcus Reed',
		loanAmount: 210000,
		termYears: 25,
		loanPurpose: 'Home',
		monthlyIncome: 7600,
		documents: [],
	});
	await completeTask(underwriterCase, 'review-application', {applicationComplete: true});
	await completeTask(underwriterCase, 'check-documents', {identityVerified: true, incomeVerified: true});
	await completeTask(underwriterCase, 'retrieve-credit-score', {creditScore: 712});
	await waitForElement(underwriterCase, 'credit-assessment');
	await api('POST', '/messages/correlation', {name: 'credit-assessment-done', correlationKey: `LA-${RUN_ID}-03`});

	// 4. A withdrawn case: the alternative stage replaced the rest of the line
	const withdrawnCase = await startLoan({
		caseId: `LA-${RUN_ID}-04`,
		applicantName: 'Tom Alvarez',
		loanAmount: 15500,
		termYears: 3,
		loanPurpose: 'Renovation',
		monthlyIncome: 3100,
		documents: [],
	});
	await completeTask(withdrawnCase, 'review-application', {applicationComplete: true});
	await api('POST', '/messages/publication', {
		name: 'application-withdrawn',
		correlationKey: `LA-${RUN_ID}-04`,
		timeToLive: 60000,
	});

	// 5. A case with a call activity: outside the case contract, shown in the simplified view
	const {processInstanceKey: bureauCase} = await api('POST', '/process-instances', {
		processDefinitionId: 'loan-with-credit-bureau',
		businessId: `LA-${RUN_ID}-05`,
		variables: {caseId: `LA-${RUN_ID}-05`, loanTitle: 'Loan for Priya Nandra — 480,000 EUR', loanAmount: 480000},
	});
	console.log(`Started LA-${RUN_ID}-05 (${bureauCase})`);
}

await main();
