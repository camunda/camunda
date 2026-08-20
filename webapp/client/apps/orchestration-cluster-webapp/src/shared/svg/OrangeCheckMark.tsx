/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {SVGProps} from 'react';

const SvgOrangeCheckMark = (props: SVGProps<SVGSVGElement>) => (
	<svg xmlns="http://www.w3.org/2000/svg" width={63} height={52} fill="none" viewBox="0 0 63 52" {...props}>
		<path fill="#FF8200" d="M63 10.2195 52.8375 0 21.5321 31.5413 10.1431 20.0664 0 30.3055 21.5321 52 63 10.2195Z" />
	</svg>
);

export {SvgOrangeCheckMark};
