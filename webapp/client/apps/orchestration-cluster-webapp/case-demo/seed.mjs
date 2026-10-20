/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* global process, Buffer */

// Deploys the demo case models and starts cases in different situations.
// Usage: node seed.mjs [--deploy-only] [--single] [--only=loan|claim|onboarding|supplier|address]
//   --single starts one case per diagram instead of every situation.
// Environment:
//   CAMUNDA_URL (or CAMUNDA_REST_ADDRESS / ZEEBE_REST_ADDRESS; default http://localhost:8080)
//   CAMUNDA_USER / CAMUNDA_PASSWORD for basic auth (default demo / demo), or for a SaaS cluster
//   CAMUNDA_CLIENT_ID, CAMUNDA_CLIENT_SECRET, CAMUNDA_OAUTH_URL and CAMUNDA_TOKEN_AUDIENCE

import {readFile, readdir} from 'node:fs/promises';
import {basename, dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const CAMUNDA_URL =
	process.env.CAMUNDA_URL ??
	process.env.CAMUNDA_REST_ADDRESS ??
	process.env.ZEEBE_REST_ADDRESS ??
	'http://localhost:8080';
const BASE_URL = `${CAMUNDA_URL}/v2`;
const WEBAPP_URL = process.env.WEBAPP_URL ?? 'http://localhost:3000';
const BASIC_AUTHORIZATION = `Basic ${Buffer.from(`${process.env.CAMUNDA_USER ?? 'demo'}:${process.env.CAMUNDA_PASSWORD ?? 'demo'}`).toString('base64')}`;
const DIRECTORY = dirname(fileURLToPath(import.meta.url));
const RUN_ID = Date.now().toString(36).slice(-4).toUpperCase();
const ONLY = process.argv.find((argument) => argument.startsWith('--only='))?.slice('--only='.length);
const SINGLE = process.argv.includes('--single');

/** Basic auth for a local cluster; a client credentials token for a SaaS cluster. */
async function getAuthorization() {
	const clientId = process.env.CAMUNDA_CLIENT_ID;

	if (clientId === undefined) {
		return BASIC_AUTHORIZATION;
	}

	const response = await fetch(process.env.CAMUNDA_OAUTH_URL ?? 'https://login.cloud.camunda.io/oauth/token', {
		method: 'POST',
		headers: {'content-type': 'application/x-www-form-urlencoded'},
		body: new URLSearchParams({
			grant_type: 'client_credentials',
			client_id: clientId,
			client_secret: process.env.CAMUNDA_CLIENT_SECRET ?? '',
			audience: process.env.CAMUNDA_TOKEN_AUDIENCE ?? 'zeebe.camunda.io',
		}),
	});

	if (!response.ok) {
		throw new Error(`Fetching an access token failed with ${response.status}: ${await response.text()}`);
	}

	const {access_token: accessToken} = await response.json();
	return `Bearer ${accessToken}`;
}

let authorization = BASIC_AUTHORIZATION;

async function api(method, path, body) {
	const isForm = body instanceof FormData;
	const response = await fetch(`${BASE_URL}${path}`, {
		method,
		headers: {
			authorization,
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
	const models = await readdir(DIRECTORY);
	// The forms and decisions go first, so the processes that use them find them.
	const files = [
		...(await readdir(join(DIRECTORY, 'forms'))).map((file) => join(DIRECTORY, 'forms', file)),
		...models.filter((file) => file.endsWith('.dmn')).map((file) => join(DIRECTORY, file)),
		...models.filter((file) => file.endsWith('.bpmn')).map((file) => join(DIRECTORY, file)),
	];
	// The server rejects a request with too many parts, so the seed deploys in batches.
	const BATCH_SIZE = 25;
	let count = 0;

	for (let start = 0; start < files.length; start += BATCH_SIZE) {
		const form = new FormData();

		for (const file of files.slice(start, start + BATCH_SIZE)) {
			form.append('resources', new Blob([await readFile(file)]), basename(file));
		}

		const {deployments} = await api('POST', '/deployments', form);
		count += deployments.length;
	}

	console.log(`Deployed ${count} resources`);
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
	// A cluster with several partitions knows a new deployment on every partition only shortly after.
	const {processInstanceKey} = await poll(`process definition ${processDefinitionId}`, async () => {
		try {
			return await api('POST', '/process-instances', {
				processDefinitionId,
				...(businessId === null ? {} : {businessId}),
				variables,
			});
		} catch (error) {
			if (/failed with 404/.test(error.message)) {
				return undefined;
			}

			throw error;
		}
	});
	console.log(
		`${(businessId ?? '(no business ID)').padEnd(16)} ${label.padEnd(70)} ${WEBAPP_URL}/tasklist/cases/${processInstanceKey}`,
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

/**
 * Plays the job worker for one case: activates jobs of the type until it gets the one of this case. Jobs of
 * other cases come back after the short timeout, so a case that should wait for a system keeps its job.
 */
async function activateJob(processInstanceKey, type) {
	return poll(`job ${type} in ${processInstanceKey}`, async () => {
		const {jobs} = await api('POST', '/jobs/activation', {
			type,
			worker: 'case-demo-seed',
			timeout: 2000,
			maxJobsToActivate: 50,
			requestTimeout: 500,
		});
		return jobs.find((job) => job.processInstanceKey === processInstanceKey);
	});
}

const leaseOf = ({jobLeaseToken}) => (jobLeaseToken ? {jobLeaseToken} : {});

async function completeJob(processInstanceKey, type, variables = {}) {
	const job = await activateJob(processInstanceKey, type);
	await api('POST', `/jobs/${job.jobKey}/completion`, {variables, ...leaseOf(job)});
}

async function throwJobError(processInstanceKey, type, errorCode, errorMessage) {
	const job = await activateJob(processInstanceKey, type);
	await api('POST', `/jobs/${job.jobKey}/error`, {errorCode, errorMessage, ...leaseOf(job)});
}

async function failJob(processInstanceKey, type, errorMessage) {
	const job = await activateJob(processInstanceKey, type);
	await api('POST', `/jobs/${job.jobKey}/failure`, {retries: 0, errorMessage, ...leaseOf(job)});
}

async function activateAdHocActivity(processInstanceKey, stageElementId, elementId) {
	const stage = await waitForElement(processInstanceKey, stageElementId);
	await api('POST', `/element-instances/ad-hoc-activities/${stage.elementInstanceKey}/activation`, {
		elements: [{elementId}],
	});
}

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

	if (SINGLE) {
		const credit = await loan('01', 'Sara Connor', 365000, 30, 'Home', 9800, documents, 'Loan: ad-hoc stage with Done');
		await completeTask(credit, 'review-application', {applicationComplete: true});
		await completeTask(credit, 'check-documents', {identityVerified: true, incomeVerified: true});
		await startCase(
			'loan-with-credit-bureau',
			`LA-${RUN_ID}-02`,
			{caseId: `LA-${RUN_ID}-02`, loanTitle: 'Loan for Priya Nandra — 480,000 EUR', loanAmount: 480000},
			'Loan: call activity (simplified view)',
		);
		return;
	}

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
// an incident, a suspended case and a case without business ID.
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

	if (SINGLE) {
		const expert = await claim('01', 'Omar Haddad', 'property', 'Claim: expert stage, nested and multi-instance tasks');
		await register(expert, 18500);
		await completeTask(expert, 'request-quote', {quote: 17200});
		return;
	}

	await claim('01', 'Lena Fischer', 'vehicle', 'Claim: new, first stage');

	const expert = await claim('02', 'Omar Haddad', 'property', 'Claim: expert stage, nested and multi-instance tasks');
	await register(expert, 18500);
	await completeTask(expert, 'request-quote', {quote: 17200});

	const skipped = await claim('03', 'Mia Wong', 'vehicle', 'Claim: expert skipped, in Coverage check');
	await register(skipped, 3200);

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

	if (SINGLE) {
		const handOver = await onboarding(
			'01',
			'Lukas Weber',
			'Frontend engineer',
			'Platform',
			'Onboarding: First day, step 2 of 3',
		);
		await prepare(handOver);
		await completeTask(handOver, 'welcome-employee', {welcomeNotes: 'Office tour done.'});
		return;
	}

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

// Supplier onboarding: stages that mix people and automation. The seed plays the job workers, so each
// case stops where a system or a timer holds it.
async function seedSuppliers() {
	const supplierIds = new Map();
	const supplier = async (number, supplierName, country, category, annualSpend, label, overrides = {}) => {
		const supplierId = `SO-${RUN_ID}-${number}`;
		const key = await startCase(
			'supplier-onboarding',
			supplierId,
			{
				supplierId,
				supplierTitle: `Supplier ${supplierName}, ${category}`,
				supplierName,
				country,
				category,
				annualSpend,
				requester: 'demo',
				creditReportTimeout: 'P2D',
				signatureReminder: 'P3D',
				firstOrderWait: 'P14D',
				...overrides,
			},
			label,
		);
		supplierIds.set(key, supplierId);
		return key;
	};
	const register = (key, name) =>
		completeTask(key, 'register-supplier', {
			vatNumber: `DE${Math.floor(100000000 + Math.random() * 899999999)}`,
			contactEmail: `orders@${name.toLowerCase().replace(/\W+/g, '-')}.example.com`,
			servicesDescription: 'Supplies parts for the assembly line.',
		});
	const registration = async (key, name) => {
		await register(key, name);
		await completeJob(key, 'sanctions-check', {sanctionsChecked: true});
	};
	const dueDiligence = async (key) => {
		await completeTask(key, 'review-risk', {riskAccepted: 'accepted', riskNotes: 'Known supplier in the region.'});
		await completeJob(key, 'credit-report', {creditLimit: 250000});
	};
	const contract = async (key) => {
		await completeTask(key, 'negotiate-terms', {paymentTerms: 'net-60', discountPercent: 3});
		await completeTask(key, 'sign-contract', {contractSigned: true});
		await waitForElement(key, 'contract-countersigned');
		await correlate('contract-countersigned', supplierIds.get(key));
	};

	if (SINGLE) {
		const diligence = await supplier(
			'01',
			'Atlas Metals SA',
			'Spain',
			'Raw materials',
			640000,
			'Supplier: Due diligence, credit report running (rules: high risk)',
		);
		await registration(diligence, 'Atlas Metals SA');
		return;
	}

	await supplier('01', 'Nordwerk GmbH', 'Germany', 'Packaging', 45000, 'Supplier: new, Registration step 1');

	const sanctions = await supplier(
		'02',
		'Baltic Freight AS',
		'Estonia',
		'Logistics',
		320000,
		'Supplier: waiting on the sanctions check (service task)',
	);
	await register(sanctions, 'Baltic Freight AS');

	const blocked = await supplier(
		'03',
		'Orion Trading Ltd',
		'Cyprus',
		'Electronics',
		80000,
		'Supplier: sanctions hit (alternative stage from a BPMN error)',
	);
	await register(blocked, 'Orion Trading Ltd');
	await throwJobError(blocked, 'sanctions-check', 'SANCTIONS_HIT', 'The supplier is on the EU sanctions list.');

	const diligence = await supplier(
		'04',
		'Atlas Metals SA',
		'Spain',
		'Raw materials',
		640000,
		'Supplier: Due diligence, credit report running (rules: high risk)',
	);
	await registration(diligence, 'Atlas Metals SA');

	const timeout = await supplier(
		'05',
		'Lumen Print Oy',
		'Finland',
		'Marketing',
		30000,
		'Supplier: credit report times out after 1 minute (boundary timer)',
		{creditReportTimeout: 'PT1M'},
	);
	await registration(timeout, 'Lumen Print Oy');

	const esg = await supplier(
		'06',
		'Verde Energia SpA',
		'Italy',
		'Energy',
		1200000,
		'Supplier: optional ESG rating running (ad-hoc service task)',
	);
	await registration(esg, 'Verde Energia SpA');
	// The ad-hoc stage completes once nothing runs in it, so the optional activity starts first.
	await activateAdHocActivity(esg, 'due-diligence', 'request-esg-rating');
	await dueDiligence(esg);

	const reminder = await supplier(
		'07',
		'Kestrel Tools BV',
		'Netherlands',
		'Tools',
		150000,
		'Supplier: Contract, reminder after 1 minute (non-interrupting timer)',
		{signatureReminder: 'PT1M'},
	);
	await registration(reminder, 'Kestrel Tools BV');
	await dueDiligence(reminder);
	await completeTask(reminder, 'negotiate-terms', {paymentTerms: 'net-30', discountPercent: 0});

	const countersign = await supplier(
		'08',
		'Fjord Textiles AS',
		'Norway',
		'Textiles',
		95000,
		'Supplier: waiting for the countersigned contract (message)',
	);
	await registration(countersign, 'Fjord Textiles AS');
	await dueDiligence(countersign);
	await completeTask(countersign, 'negotiate-terms', {paymentTerms: 'net-60', discountPercent: 2});
	await completeTask(countersign, 'sign-contract', {contractSigned: true});

	const firstOrder = await supplier(
		'09',
		'Helios Components Kft',
		'Hungary',
		'Electronics',
		210000,
		'Supplier: Activation, waiting 14 days for the first order (timer)',
	);
	await registration(firstOrder, 'Helios Components Kft');
	await dueDiligence(firstOrder);
	await contract(firstOrder);
	await completeJob(firstOrder, 'erp-create-supplier', {erpSupplierNumber: 'V-100481'});

	const erpIncident = await supplier(
		'10',
		'Pacific Rim Plastics Ltd',
		'Ireland',
		'Plastics',
		180000,
		'Supplier: incident, the ERP rejected the supplier (failed job)',
	);
	await registration(erpIncident, 'Pacific Rim Plastics Ltd');
	await dueDiligence(erpIncident);
	await contract(erpIncident);
	await failJob(erpIncident, 'erp-create-supplier', 'ERP: the VAT number is already assigned to vendor V-099120.');

	const ordered = await supplier(
		'11',
		'Alpine Coffee AG',
		'Switzerland',
		'Catering',
		24000,
		'Supplier: first order due after 1 minute, then Review the first order',
		{firstOrderWait: 'PT1M'},
	);
	await registration(ordered, 'Alpine Coffee AG');
	await dueDiligence(ordered);
	await contract(ordered);
	await completeJob(ordered, 'erp-create-supplier', {erpSupplierNumber: 'V-100502'});
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
	authorization = await getAuthorization();
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

	if (ONLY === undefined || ONLY === 'supplier') {
		await seedSuppliers();
	}

	if (ONLY === undefined || ONLY === 'address') {
		await seedAddressChange();
	}
}

await main();
