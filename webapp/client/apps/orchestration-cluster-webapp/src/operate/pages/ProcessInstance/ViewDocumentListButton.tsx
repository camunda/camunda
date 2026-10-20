/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {createPortal} from 'react-dom';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {View} from '@carbon/react/icons';
import type {DocumentInfo} from '#/operate/shared/Documents/documentInfo';
import {DocumentListModal} from '#/operate/shared/Documents/DocumentListModal';
import {parseDocumentVariable} from './parseDocumentVariable';
import {variableQuery} from './variables.queries';

type Props = {
	documents: DocumentInfo[];
	isLowerBound: boolean;
	variableKey: string;
	variableName: string;
};

const VariableDocumentListModal: React.FC<Props & {onClose: () => void}> = ({
	documents,
	isLowerBound,
	variableKey,
	variableName,
	onClose,
}) => {
	const {t} = useTranslation();
	const {data, isError, isLoading} = useQuery({...variableQuery(variableKey), enabled: isLowerBound});
	const fullResult = !isLowerBound || data === undefined ? null : parseDocumentVariable(data.value, false);
	const resolvedDocuments =
		fullResult === null ? documents : fullResult.type === 'list' ? fullResult.documents : [fullResult.document];

	return (
		<DocumentListModal
			documents={resolvedDocuments}
			labelSuffix={variableName}
			onClose={onClose}
			loadingHint={isLoading ? t('operate.processInstance.variables.documentsLoadingHint') : undefined}
			errorHint={isError ? t('operate.processInstance.variables.documentsErrorHint') : undefined}
		/>
	);
};

const ViewDocumentListButton: React.FC<Props> = (props) => {
	const {t} = useTranslation();
	const [isOpen, setIsOpen] = useState(false);

	return (
		<>
			<Button
				kind="ghost"
				size="sm"
				hasIconOnly
				renderIcon={View}
				iconDescription={t('operate.processInstance.variables.viewDocuments')}
				tooltipPosition="top"
				tooltipAlignment="end"
				aria-label={t('operate.processInstance.variables.viewDocumentsLabel', {name: props.variableName})}
				onClick={() => setIsOpen(true)}
			/>
			{isOpen &&
				createPortal(<VariableDocumentListModal {...props} onClose={() => setIsOpen(false)} />, window.document.body)}
		</>
	);
};

export {ViewDocumentListButton};
