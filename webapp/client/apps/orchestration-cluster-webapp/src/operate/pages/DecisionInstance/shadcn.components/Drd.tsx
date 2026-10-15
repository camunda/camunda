/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {Button, IconButton, Text} from '@camunda/design-system';
import {Maximize2, Minimize, X} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {ForbiddenError} from '#/shared/errors';
import {DrdUnavailableError} from '#/operate/shared/DecisionViewer/DrdJS';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {DiagramShell} from '#/operate/shared/DiagramShell/shadcn.components/DiagramShell';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {cn} from '#/shared/cn';
import {useDecisionInstance} from '../decisionInstance.queries';
import {useDrdData} from '../drdData.queries';
import {useDecisionDefinitionXml} from '../useDecisionDefinitionXml';
import {DrdViewer} from './DrdViewer';
import type {DrdPanelState} from '../useDrdPanelState';

type Props = {
	decisionEvaluationInstanceKey: string;
	decisionEvaluationKey?: string;
	decisionDefinitionKey?: string;
	decisionDefinitionId?: string;
	drdPanelState: Exclude<DrdPanelState, 'closed'>;
	onChangeDrdPanelState: (state: DrdPanelState) => void;
	modeButtonRef: React.Ref<HTMLButtonElement>;
};

const Drd: React.FC<Props> = ({
	decisionEvaluationInstanceKey,
	decisionEvaluationKey,
	decisionDefinitionKey,
	decisionDefinitionId,
	drdPanelState,
	onChangeDrdPanelState,
	modeButtonRef,
}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [definitionsName, setDefinitionsName] = useState('');
	const [renderError, setRenderError] = useState<Error | null>(null);
	const drdData = useDrdData(decisionEvaluationKey);
	const definitionXml = useDecisionDefinitionXml(decisionDefinitionKey);
	const isForbidden = drdData.error instanceof ForbiddenError || definitionXml.error instanceof ForbiddenError;
	const isUnavailable = renderError instanceof DrdUnavailableError;
	const isError = drdData.isError || definitionXml.isError || (renderError !== null && !isUnavailable);
	const instanceName = useDecisionInstance(decisionEvaluationInstanceKey).query.data?.decisionDefinitionName;
	const isLoading = !isError && (drdData.data === undefined || definitionXml.data === undefined);
	const modeLabel =
		drdPanelState === 'minimized'
			? t('operate.decisionInstance.ds.drd.maximize')
			: t('operate.decisionInstance.ds.drd.minimize');

	return (
		<div
			data-testid="drd"
			role={drdPanelState === 'maximized' ? 'region' : undefined}
			aria-label={drdPanelState === 'maximized' ? t('operate.decisionInstance.ds.drd.title') : undefined}
			className="grid h-full w-full grid-rows-[3rem_minmax(0,1fr)] bg-[var(--neutral-background-subtle)]"
			onKeyDown={(event) => {
				if (event.key === 'Escape' && drdPanelState === 'maximized') {
					onChangeDrdPanelState('minimized');
				}
			}}
		>
			<PanelHeader title={definitionsName || t('operate.decisionInstance.ds.drd.title')} size="md">
				{drdPanelState === 'maximized' && (
					<Text as="span" variant="body-sm" className="ml-4 truncate text-neutral-foreground-subtle">
						{instanceName ? `${instanceName} · ${decisionEvaluationInstanceKey}` : decisionEvaluationInstanceKey}
					</Text>
				)}
				<div className="ml-auto flex items-center">
					<IconButton
						id="operate-decision-drd-mode-button"
						variant="ghost"
						size="lg"
						icon={drdPanelState === 'minimized' ? Maximize2 : Minimize}
						label={modeLabel}
						tooltipSide="left"
						ref={modeButtonRef}
						onClick={() => onChangeDrdPanelState(drdPanelState === 'minimized' ? 'maximized' : 'minimized')}
					/>
					<IconButton
						variant="ghost"
						size="lg"
						icon={X}
						label={t('operate.decisionInstance.ds.drd.close')}
						tooltipSide="left"
						onClick={() => onChangeDrdPanelState('closed')}
					/>
				</div>
			</PanelHeader>
			<div className="relative min-h-0 overflow-hidden">
				<DiagramShell
					status={isForbidden ? 'forbidden' : isLoading ? 'loading' : 'content'}
					contentLabel={t('operate.decisionInstance.ds.drd.content')}
				>
					{!isForbidden && renderError === null && drdData.data !== undefined && definitionXml.data !== undefined && (
						<DrdViewer
							xml={definitionXml.data}
							data={drdData.data}
							selectedDecisionEvaluationInstanceKey={decisionEvaluationInstanceKey}
							selectedDecisionDefinitionId={decisionDefinitionId}
							onDecisionSelection={(selectedKey) => {
								void navigate({
									to: '/operate-preview/decisions/$decisionInstanceId',
									params: {decisionInstanceId: selectedKey},
									state: (state) => ({
										...state,
										operateDecisionFocus: {decisionInstanceKey: selectedKey},
									}),
								});
							}}
							onDefinitionsChange={(definitions) => setDefinitionsName(definitions?.name ?? definitions?.id ?? '')}
							onError={setRenderError}
						/>
					)}
				</DiagramShell>
				{isUnavailable && (
					<EmptyMessage
						className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
						message={t('operate.decisionInstance.ds.drd.unavailableTitle')}
						additionalInfo={t('operate.decisionInstance.ds.drd.unavailableInfo')}
					/>
				)}
				{isError && !isForbidden && (
					<div
						className={cn(
							'pointer-events-none absolute left-1/2 top-1/2 z-10 flex -translate-x-1/2 -translate-y-1/2 flex-col items-center justify-center gap-5 bg-[var(--neutral-background-subtle)] p-5',
						)}
					>
						<ErrorMessage />
						<Button
							type="button"
							variant="secondary"
							size="sm"
							className="pointer-events-auto"
							onClick={() => {
								if (drdData.isError) {
									void drdData.refetch();
								}
								if (definitionXml.isError || renderError !== null) {
									void definitionXml.refetch().then(({isSuccess}) => {
										if (isSuccess) {
											setRenderError(null);
										}
									});
								}
							}}
						>
							{t('errorGenericErrorPageButtonLabel')}
						</Button>
					</div>
				)}
			</div>
		</div>
	);
};

export {Drd};
