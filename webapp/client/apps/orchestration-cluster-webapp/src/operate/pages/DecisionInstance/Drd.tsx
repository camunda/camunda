/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {Button} from '@carbon/react';
import {Close, Maximize, Minimize} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import {ForbiddenError} from '#/shared/errors';
import {Header as PanelHeader} from '#/operate/shared/PanelHeader/styled';
import {Title as PanelTitle} from '#/operate/shared/PanelTitle/styled';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {useDrdData} from './drdData.queries';
import {useDecisionDefinitionXml} from './useDecisionDefinitionXml';
import {DrdViewer} from './DrdViewer';
import {DrdBody, DrdContainer, DrdControls, DrdRetry} from './Drd.styled';
import type {DrdPanelState} from './useDrdPanelState';

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
	const isError = drdData.isError || definitionXml.isError || renderError !== null;
	const isLoading = !isError && (drdData.data === undefined || definitionXml.data === undefined);

	return (
		<DrdContainer
			data-testid="drd"
			role={drdPanelState === 'maximized' ? 'region' : undefined}
			aria-label={drdPanelState === 'maximized' ? t('operate.decisionInstance.drd.title') : undefined}
		>
			<PanelHeader as="div" $size="md">
				<PanelTitle>{definitionsName || t('operate.decisionInstance.drd.title')}</PanelTitle>
				<DrdControls orientation="horizontal">
					{drdPanelState === 'minimized' ? (
						<Button
							id="operate-decision-drd-mode-button"
							kind="ghost"
							hasIconOnly
							renderIcon={Maximize}
							tooltipPosition="left"
							iconDescription={t('operate.decisionInstance.drd.maximize')}
							aria-label={t('operate.decisionInstance.drd.maximize')}
							size="lg"
							ref={modeButtonRef}
							onClick={() => onChangeDrdPanelState('maximized')}
						/>
					) : (
						<Button
							id="operate-decision-drd-mode-button"
							kind="ghost"
							hasIconOnly
							renderIcon={Minimize}
							tooltipPosition="left"
							iconDescription={t('operate.decisionInstance.drd.minimize')}
							aria-label={t('operate.decisionInstance.drd.minimize')}
							size="lg"
							ref={modeButtonRef}
							onClick={() => onChangeDrdPanelState('minimized')}
						/>
					)}
					<Button
						kind="ghost"
						hasIconOnly
						renderIcon={Close}
						tooltipPosition="left"
						iconDescription={t('operate.decisionInstance.drd.close')}
						aria-label={t('operate.decisionInstance.drd.close')}
						size="lg"
						onClick={() => onChangeDrdPanelState('closed')}
					/>
				</DrdControls>
			</PanelHeader>
			<DrdBody>
				<DiagramShell status={isForbidden ? 'forbidden' : isLoading ? 'loading' : 'content'}>
					{!isForbidden && renderError === null && drdData.data !== undefined && definitionXml.data !== undefined && (
						<DrdViewer
							xml={definitionXml.data}
							data={drdData.data}
							selectedDecisionEvaluationInstanceKey={decisionEvaluationInstanceKey}
							selectedDecisionDefinitionId={decisionDefinitionId}
							onDecisionSelection={(selectedKey) => {
								void navigate({
									to: '/operate/decisions/$decisionInstanceId',
									params: {decisionInstanceId: selectedKey},
								}).then(() => {
									(
										document.getElementById('operate-decision-drd-mode-button') ??
										document.getElementById('operate-decision-instance-heading')
									)?.focus();
								});
							}}
							onDefinitionsChange={(definitions) => setDefinitionsName(definitions?.name ?? '')}
							onError={setRenderError}
						/>
					)}
				</DiagramShell>
				{isError && !isForbidden && (
					<DrdRetry>
						<ErrorMessage />
						<Button
							kind="tertiary"
							size="sm"
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
					</DrdRetry>
				)}
			</DrdBody>
		</DrdContainer>
	);
};

export {Drd};
