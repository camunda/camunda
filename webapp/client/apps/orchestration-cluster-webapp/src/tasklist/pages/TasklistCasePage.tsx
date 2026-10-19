/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {CASE_HEADER_VARIABLE, getCaseHeader, getCaseTitle, getDocumentReferences} from '@camunda/case-management-sdk';
import {Card, CardContent, CardHeader, CardTitle, PageLayout} from '@camunda/design-system';
import type {CaseData} from '#/tasklist/modules/case-details/caseData';
import {CaseDetailsCard} from '#/tasklist/modules/case-details/components/CaseDetailsCard';
import {CaseDiagramCard} from '#/tasklist/modules/case-details/components/CaseDiagramCard';
import {CaseDocumentsCard} from '#/tasklist/modules/case-details/components/CaseDocumentsCard';
import {CaseHeader} from '#/tasklist/modules/case-details/components/CaseHeader';
import {CaseIncidentBanner} from '#/tasklist/modules/case-details/components/CaseIncidentBanner';
import {StageLine} from '#/tasklist/modules/case-details/components/StageLine';
import {getCaseView} from '#/tasklist/modules/case-details/getCaseView';
import {extractVariablesFromFormSchema} from '#/tasklist/modules/form-js/extractVariablesFromFormSchema';

type Props = CaseData & {
	caseFormSchema: string | null;
	/** The work area: the tasks of the case, or one task. */
	children: React.ReactNode;
};

const TasklistCasePage: React.FC<Props> = ({caseFormSchema, children, ...caseData}) => {
	const {t} = useTranslation();
	const {processInstance, xml, elementInstances, userTasks, rootVariables} = caseData;
	const {model, isSimplifiedView, progress} = useMemo(
		() => getCaseView({processInstance, xml, elementInstances, userTasks, rootVariables}),
		[processInstance, xml, elementInstances, userTasks, rootVariables],
	);
	const processName = processInstance.processDefinitionName || processInstance.processDefinitionId;
	const title = getCaseTitle(rootVariables.find(({name}) => name === model.caseMarker?.titleVariable)) ?? processName;
	const header = getCaseHeader(rootVariables.find(({name}) => name === CASE_HEADER_VARIABLE)) ?? [];
	const documents = useMemo(() => getDocumentReferences(rootVariables), [rootVariables]);
	const caseFormVariables = useMemo(() => {
		const names = new Set(caseFormSchema === null ? [] : extractVariablesFromFormSchema(caseFormSchema));
		return rootVariables.filter(({name}) => names.has(name));
	}, [caseFormSchema, rootVariables]);

	return (
		<PageLayout>
			<div className="flex flex-col gap-4">
				<CaseHeader
					title={title}
					processName={processName}
					caseId={processInstance.businessId ?? processInstance.processInstanceKey}
					state={processInstance.state}
					hasIncident={processInstance.hasIncident}
					startDate={processInstance.startDate}
					header={isSimplifiedView ? [] : header}
				/>

				{processInstance.hasIncident ? <CaseIncidentBanner /> : null}

				{isSimplifiedView ? <CaseDiagramCard xml={xml} elementInstances={elementInstances} /> : null}

				{progress.length === 0 ? null : (
					<Card>
						<CardHeader>
							<CardTitle>{t('tasklist.caseProgressTitle')}</CardTitle>
						</CardHeader>
						<CardContent>
							<StageLine progress={progress} />
						</CardContent>
					</Card>
				)}

				<div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_24rem]">
					<div className="flex min-w-0 flex-col gap-4">
						{children}
						{caseFormSchema === null || isSimplifiedView ? null : (
							<CaseDetailsCard formSchema={caseFormSchema} variables={caseFormVariables} />
						)}
					</div>
					<div className="flex min-w-0 flex-col gap-4">
						<CaseDocumentsCard documents={documents} />
					</div>
				</div>
			</div>
		</PageLayout>
	);
};

export {TasklistCasePage};
