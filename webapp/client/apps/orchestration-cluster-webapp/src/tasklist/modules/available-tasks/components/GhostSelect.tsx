/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Select} from '#/shared/design-system-compat';

type GhostSelectProps = React.ComponentProps<typeof Select>;

const GhostSelect = (props: GhostSelectProps) => {
	return <Select {...props} />;
};

export {GhostSelect};
