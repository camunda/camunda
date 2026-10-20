/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text} from '@camunda/design-system';
import type {HexColor} from '#/tasklist/modules/theme-editor/colorRamp';
import {HexField} from './HexField';

type Props = {
	label: string;
	hexLabel: string;
	hex: HexColor;
	onChange: (hex: HexColor) => void;
};

const ColorField: React.FC<Props> = ({label, hexLabel, hex, onChange}) => (
	<div className="flex flex-col gap-1.5">
		<Text as="span" variant="helper" className="text-neutral-foreground-subtle">
			{label}
		</Text>
		<HexField label={hexLabel} hex={hex} onChange={onChange} />
	</div>
);

export {ColorField};
