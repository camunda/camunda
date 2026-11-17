/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import styled from 'styled-components';
import {getElementIconSvg, getTemplateIconSource, resolveElementIcon} from './elementIcons';

const Frame = styled.span`
	display: inline-flex;
	flex-shrink: 0;
	svg {
		width: 26px;
		height: 26px;
		fill: currentColor;
	}
	img {
		width: 26px;
		height: 26px;
		box-sizing: border-box;
		padding: 4px;
		object-fit: contain;
	}
	&[data-icon^='gateway-'] {
		position: relative;
		top: 3px;
		right: 2px;
	}
`;

function ElementInstanceIcon({businessObject, root = false}: {businessObject?: BusinessObject; root?: boolean}) {
	const [failedIcon, setFailedIcon] = useState<string>();
	const template = getTemplateIconSource(businessObject, root, failedIcon);
	const name = resolveElementIcon(businessObject, root);
	return template ? (
		<Frame data-testid="element-instance-icon">
			<img key={template} src={template} alt="" aria-hidden="true" onError={() => setFailedIcon(template)} />
		</Frame>
	) : (
		<Frame
			data-testid="element-instance-icon"
			aria-hidden="true"
			data-icon={name}
			dangerouslySetInnerHTML={{__html: getElementIconSvg(name)}}
		/>
	);
}

export {ElementInstanceIcon};
