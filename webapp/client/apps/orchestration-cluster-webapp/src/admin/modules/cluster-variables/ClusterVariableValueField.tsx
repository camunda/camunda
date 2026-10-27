/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Label} from '@camunda/design-system';
import {RichTextEditor} from '#/operate/shared/Editors/shadcn.components/RichTextEditor';

type Props = {
	id: string;
	label: string;
	value: string;
	onChange?: (value: string) => void;
	readOnly?: boolean;
	autoFocus?: boolean;
	errorMessage?: string;
};

const ClusterVariableValueField: React.FC<Props> = ({
	id,
	label,
	value,
	onChange,
	readOnly = false,
	autoFocus = false,
	errorMessage,
}) => (
	<div className="flex flex-col gap-1.5">
		<Label htmlFor={`${id}-editor`}>{label}</Label>
		<div className={`overflow-hidden rounded-md border ${errorMessage ? 'border-danger' : 'border-input'}`}>
			<RichTextEditor
				id={id}
				language="json"
				value={value}
				onChange={onChange}
				readOnly={readOnly}
				autoFocus={autoFocus}
				isInvalid={Boolean(errorMessage)}
				height="240px"
				options={{ariaLabel: label}}
			/>
		</div>
		{errorMessage ? (
			<span role="alert" className="text-xs text-danger-foreground-subtle">
				{errorMessage}
			</span>
		) : null}
	</div>
);

export {ClusterVariableValueField};
