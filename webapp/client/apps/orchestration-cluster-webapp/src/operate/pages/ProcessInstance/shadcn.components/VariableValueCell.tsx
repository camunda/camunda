/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {useQueryClient} from '@tanstack/react-query';
import {InlineJsonEditor} from '#/operate/shared/Editors/shadcn.components/InlineJsonEditor';
import {variableQuery} from '../variables.queries';
import type {DocumentParseResult} from '../parseDocumentVariable';
import {DocumentValueCell} from './DocumentValueCell';
import {ExistingVariableValue} from './ExistingVariableValue';
import {ViewFullVariableButton} from './ViewFullVariableButton';

type Props = {
	name: string;
	value: string;
	variableKey: string;
	isTruncated: boolean;
	documentResult: DocumentParseResult | null;
	isEditing: boolean;
	canEdit: boolean;
};

const VariableValueCell: React.FC<Props> = ({
	name,
	value,
	variableKey,
	isTruncated,
	documentResult,
	isEditing,
	canEdit,
}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();

	if (isEditing) {
		return (
			<ExistingVariableValue
				variableKey={variableKey}
				variableName={name}
				variableValue={value}
				isTruncated={isTruncated}
			/>
		);
	}

	if (documentResult !== null) {
		return <DocumentValueCell result={documentResult} />;
	}

	return (
		<InlineJsonEditor
			value={value}
			label={name}
			isTruncatedValue={isTruncated}
			readOnly
			onCopy={isTruncated ? async () => (await queryClient.fetchQuery(variableQuery(variableKey))).value : undefined}
			renderButton={
				isTruncated
					? () => (
							<ViewFullVariableButton
								variableKey={variableKey}
								variableName={name}
								buttonLabel={t('operate.processInstance.variables.showAll')}
								canEdit={canEdit}
							/>
						)
					: undefined
			}
		/>
	);
};

export {VariableValueCell};
