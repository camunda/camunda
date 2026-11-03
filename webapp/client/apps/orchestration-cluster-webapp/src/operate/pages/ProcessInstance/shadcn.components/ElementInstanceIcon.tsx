/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {getElementIconSvg, getTemplateIconSource, resolveElementIcon} from '../elementIcons';

const FRAME_CLASS_NAME =
	'inline-flex shrink-0 [&_svg]:size-[26px] [&_svg]:fill-current [&_img]:box-border [&_img]:size-[26px] [&_img]:object-contain [&_img]:p-1 data-[icon^=gateway-]:relative data-[icon^=gateway-]:right-0.5 data-[icon^=gateway-]:top-[3px]';

const ElementInstanceIcon: React.FC<{businessObject?: BusinessObject; root?: boolean}> = ({
	businessObject,
	root = false,
}) => {
	const [failedIcon, setFailedIcon] = useState<string>();
	const template = getTemplateIconSource(businessObject, root, failedIcon);
	const name = resolveElementIcon(businessObject, root);

	return template ? (
		<span className={FRAME_CLASS_NAME}>
			<img key={template} src={template} alt="" aria-hidden="true" onError={() => setFailedIcon(template)} />
		</span>
	) : (
		<span
			className={FRAME_CLASS_NAME}
			aria-hidden="true"
			data-icon={name}
			dangerouslySetInnerHTML={{__html: getElementIconSvg(name)}}
		/>
	);
};

export {ElementInstanceIcon};
