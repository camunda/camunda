/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* global process, Buffer */

// Deploys the demo case models and starts cases in different situations.
// Usage: node seed.mjs [--deploy-only] [--only=loan|claim|onboarding|address]
// Environment: CAMUNDA_URL (default http://localhost:8080), CAMUNDA_USER / CAMUNDA_PASSWORD (default demo / demo)

import {readFile, readdir} from 'node:fs/promises';
import {basename, dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const CAMUNDA_URL = process.env.CAMUNDA_URL ?? 'http://localhost:8080';
const BASE_URL = `${CAMUNDA_URL}/v2`;
const WEBAPP_URL = process.env.WEBAPP_URL ?? 'http://localhost:3000';
const AUTHORIZATION = `Basic ${Buffer.from(`${process.env.CAMUNDA_USER ?? 'demo'}:${process.env.CAMUNDA_PASSWORD ?? 'demo'}`).toString('base64')}`;
const DIRECTORY = dirname(fileURLToPath(import.meta.url));
const RUN_ID = Date.now().toString(36).slice(-4).toUpperCase();
const ONLY = process.argv.find((argument) => argument.startsWith('--only='))?.slice('--only='.length);

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

async function poll(description, check) {
	for (let attempt = 0; attempt < 40; attempt++) {
		const result = await check();

		if (result !== undefined) {
			return result;
		}

		await sleep(500);
	}

	throw new Error(`Timed out: ${description}`);
}

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

async function startCase(processDefinitionId, businessId, variables, label) {
	const {processInstanceKey} = await api('POST', '/process-instances', {
		processDefinitionId,
		...(businessId === null ? {} : {businessId}),
		variables,
	});
	console.log(
		`${(businessId ?? '(no business ID)').padEnd(16)} ${label.padEnd(58)} ${WEBAPP_URL}/tasklist/cases/${processInstanceKey}`,
	);
	return processInstanceKey;
}

/** Waits until the case has an open user task for the element, then completes it. */
async function completeTask(processInstanceKey, elementId, variables = {}) {
	const task = await poll(`open task ${elementId} in ${processInstanceKey}`, async () => {
		const {items} = await api('POST', '/user-tasks/search', {
			filter: {processInstanceKey, elementId, state: 'CREATED'},
		});
		return items[0];
	});

	await api('POST', `/user-tasks/${task.userTaskKey}/completion`, {variables});
}

async function waitForElement(processInstanceKey, elementId) {
	return poll(`active element ${elementId} in ${processInstanceKey}`, async () => {
		const {items} = await api('POST', '/element-instances/search', {
			filter: {processInstanceKey, elementId, state: 'ACTIVE'},
		});
		return items[0];
	});
}

const correlate = (name, correlationKey) => api('POST', '/messages/correlation', {name, correlationKey});

// Loan cases: one case marker, five stages, an ad-hoc stage with manual completion, an alternative stage.
async function seedLoans(documents) {
	const loan = (number, applicantName, loanAmount, termYears, loanPurpose, monthlyIncome, loanDocuments, label) =>
		startCase(
			'loan-application',
			`LA-${RUN_ID}-${number}`,
			{
				caseId: `LA-${RUN_ID}-${number}`,
				loanTitle: `Loan for ${applicantName} — ${loanAmount.toLocaleString('en')} EUR`,
				applicantName,
				applicantEmail: `${applicantName.toLowerCase().replace(/\W+/g, '.')}@example.com`,
				loanAmount,
				termYears,
				loanPurpose,
				monthlyIncome,
				loanOfficer: 'demo',
				documents: loanDocuments,
			},
			label,
		);

	await loan('01', 'Jane Doe', 25000, 5, 'Car', 4200, documents, 'Loan: new, first stage');

	const credit = await loan('02', 'Sara Connor', 365000, 30, 'Home', 9800, documents, 'Loan: ad-hoc stage with Done');
	await completeTask(credit, 'review-application', {applicationComplete: true});
	await completeTask(credit, 'check-documents', {identityVerified: true, incomeVerified: true});

	const underwriter = await loan('03', 'Marcus Reed', 210000, 25, 'Home', 7600, [], 'Loan: past Done, in review');
	await completeTask(underwriter, 'review-application', {applicationComplete: true});
	await completeTask(underwriter, 'check-documents', {identityVerified: true, incomeVerified: true});
	await completeTask(underwriter, 'retrieve-credit-score', {creditScore: 712});
	await waitForElement(underwriter, 'credit-assessment');
	await correlate('credit-assessment-done', `LA-${RUN_ID}-03`);

	const withdrawn = await loan(
		'04',
		'Tom Alvarez',
		15500,
		3,
		'Renovation',
		3100,
		[],
		'Loan: withdrawn (alternative stage)',
	);
	await completeTask(withdrawn, 'review-application', {applicationComplete: true});
	await api('POST', '/messages/publication', {
		name: 'application-withdrawn',
		correlationKey: `LA-${RUN_ID}-04`,
		timeToLive: 60000,
	});

	await startCase(
		'loan-with-credit-bureau',
		`LA-${RUN_ID}-05`,
		{caseId: `LA-${RUN_ID}-05`, loanTitle: 'Loan for Priya Nandra — 480,000 EUR', loanAmount: 480000},
		'Loan: call activity (simplified view)',
	);
}

// Insurance claims: deep nesting, a skipped stage, a rework loop, three alternative stages,
// an optional stage (non-interrupting), an incident, a suspended case and a case without business ID.
async function seedClaims(documents) {
	const claim = (number, claimantName, claimType, label, overrides = {}, businessId = `CL-${RUN_ID}-${number}`) =>
		startCase(
			'insurance-claim',
			businessId,
			{
				claimId: `CL-${RUN_ID}-${number}`,
				claimTitle: `${claimType === 'vehicle' ? 'Car accident' : 'Water damage'} — ${claimantName}`,
				claimantName,
				policyNumber: `POL-${4400 + Number(number)}`,
				claimType,
				claimDeadline: 'P30D',
				damagePhotos: documents,
				...overrides,
			},
			label,
		);
	const register = (key, estimatedDamage, fraudSuspected = false) =>
		completeTask(key, 'register-claim', {
			estimatedDamage,
			fraudSuspected,
			incidentDate: '2026-10-12',
			claimDescription: 'Damage reported by the customer.',
		});
	const coverage = async (key) => {
		await completeTask(key, 'check-policy', {policyValid: true});
		await completeTask(key, 'check-deductible', {deductible: 500});
	};

	await claim('01', 'Lena Fischer', 'vehicle', 'Claim: new, first stage');

	const expert = await claim('02', 'Omar Haddad', 'property', 'Claim: expert stage, nested and multi-instance tasks');
	await register(expert, 18500);
	await completeTask(expert, 'request-quote', {quote: 17200});

	const optional = await claim('03', 'Mia Wong', 'vehicle', 'Claim: expert skipped, optional stage running');
	await register(optional, 3200);
	await waitForElement(optional, 'coverage-check');
	await correlate('customer-called', `CL-${RUN_ID}-03`);

	const rework = await claim('04', 'Jonas Berg', 'vehicle', 'Claim: rework loop back to Coverage check');
	await register(rework, 4100);
	await coverage(rework);
	await completeTask(rework, 'decide-claim', {
		decision: 'needMoreInfo',
		decisionNotes: 'The police report is missing.',
	});

	const fraud = await claim('05', 'Victor Kane', 'property', 'Claim: fraud (alternative stage from an escalation)');
	await register(fraud, 54000, true);

	await claim('06', 'Ana Silva', 'vehicle', 'Claim: expires in 2 minutes (timer alternative stage)', {
		claimDeadline: 'PT2M',
	});

	const incident = await claim('07', 'Paul Meyer', 'vehicle', 'Claim: incident in the first stage');
	await completeTask(incident, 'register-claim', {estimatedDamage: 2500, incidentDate: '2026-10-14'});

	const payout = await claim('08', 'Grace Lee', 'vehicle', 'Claim: approved, in payout');
	await register(payout, 6800);
	await coverage(payout);
	await completeTask(payout, 'decide-claim', {decision: 'approved', payoutAmount: 6300});

	const withdrawn = await claim('09', 'Noah Brooks', 'property', 'Claim: withdrawn (alternative stage)');
	await register(withdrawn, 2100);
	await waitForElement(withdrawn, 'coverage-check');
	await correlate('claim-withdrawn', `CL-${RUN_ID}-09`);

	const suspended = await claim('10', 'Eva Novak', 'vehicle', 'Claim: suspended');
	await waitForElement(suspended, 'register-claim');
	await api('POST', `/process-instances/${suspended}/suspension`, {});

	await claim('11', 'Liam Doyle', 'property', 'Claim: no business ID', {}, null);

	const rejected = await claim('12', 'Zoe Martin', 'vehicle', 'Claim: rejected, completed (not in the list)');
	await register(rejected, 900);
	await coverage(rejected);
	await completeTask(rejected, 'decide-claim', {decision: 'rejected', decisionNotes: 'Below the deductible.'});
}

// Employee onboarding: stages with sequential tasks, a loop inside a stage, an automatic step, an alternative stage.
async function seedOnboardings() {
	const onboarding = (number, employeeName, role, team, label) =>
		startCase(
			'employee-onboarding',
			`ON-${RUN_ID}-${number}`,
			{
				onboardingId: `ON-${RUN_ID}-${number}`,
				onboardingTitle: `Onboarding of ${employeeName}, ${role}`,
				employeeName,
				role,
				team,
				startDate: '2026-11-02',
				manager: 'demo',
			},
			label,
		);
	const prepare = async (key) => {
		await completeTask(key, 'create-contract', {contractType: 'permanent', salary: 62000});
		await completeTask(key, 'collect-signed-contract', {contractSigned: true});
		await completeTask(key, 'order-equipment', {laptopModel: 'developer', phoneNeeded: false});
	};
	const firstDay = async (key) => {
		await completeTask(key, 'welcome-employee', {welcomeNotes: 'Team lunch at noon.'});
		await completeTask(key, 'hand-over-equipment', {equipmentHandedOver: true});
		await completeTask(key, 'set-up-accounts', {emailAddress: 'new.hire@example.com', accountsCreated: true});
	};
	const train = async (key) => {
		await completeTask(key, 'complete-safety-training', {safetyTrainingPassed: true});
		await completeTask(key, 'shadow-colleague', {buddy: 'Ana Silva', shadowingNotes: 'Release process'});
		await completeTask(key, 'sign-off-training', {trainingSignedOff: true});
	};

	await onboarding('01', 'Hannah Schulz', 'Backend engineer', 'Platform', 'Onboarding: new, step 1 of Preparation');

	const signing = await onboarding('02', 'Diego Ramos', 'Designer', 'Experience', 'Onboarding: step 2 of Preparation');
	await completeTask(signing, 'create-contract', {contractType: 'fixed-term', salary: 54000});

	const followUp = await onboarding(
		'03',
		'Aisha Khan',
		'Data analyst',
		'Insights',
		'Onboarding: contract not signed, loop in the stage',
	);
	await completeTask(followUp, 'create-contract', {contractType: 'permanent', salary: 58000});
	await completeTask(followUp, 'collect-signed-contract', {contractSigned: false});

	const handOver = await onboarding(
		'04',
		'Lukas Weber',
		'Frontend engineer',
		'Platform',
		'Onboarding: First day, step 2 of 3',
	);
	await prepare(handOver);
	await completeTask(handOver, 'welcome-employee', {welcomeNotes: 'Office tour done.'});

	const signOff = await onboarding(
		'05',
		'Sofia Rossi',
		'Product manager',
		'Growth',
		'Onboarding: Training, step 3 of 3',
	);
	await prepare(signOff);
	await firstDay(signOff);
	await completeTask(signOff, 'complete-safety-training', {safetyTrainingPassed: true});
	await completeTask(signOff, 'shadow-colleague', {buddy: 'Lena Fischer', shadowingNotes: 'Roadmap planning'});

	const review = await onboarding(
		'06',
		'Ben Carter',
		'Support engineer',
		'Customer care',
		'Onboarding: Probation, final review',
	);
	await prepare(review);
	await firstDay(review);
	await train(review);
	await completeTask(review, 'mid-probation-review', {midProbationRating: 'on-track'});

	const declined = await onboarding(
		'07',
		'Chloe Martin',
		'Recruiter',
		'People',
		'Onboarding: offer declined (alternative stage)',
	);
	await completeTask(declined, 'create-contract', {contractType: 'permanent', salary: 51000});
	await waitForElement(declined, 'collect-signed-contract');
	await correlate('offer-declined', `ON-${RUN_ID}-07`);
}

// A process without a case marker: the case page shows it without stages.
async function seedAddressChange() {
	await startCase(
		'address-change',
		`AC-${RUN_ID}-01`,
		{customerName: 'Lena Fischer'},
		'Address change: process without a case marker',
	);
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

	if (ONLY === undefined || ONLY === 'loan') {
		await seedLoans(documents);
	}

	if (ONLY === undefined || ONLY === 'claim') {
		await seedClaims(documents);
	}

	if (ONLY === undefined || ONLY === 'onboarding') {
		await seedOnboardings();
	}

	if (ONLY === undefined || ONLY === 'address') {
		await seedAddressChange();
	}
}

await main();
