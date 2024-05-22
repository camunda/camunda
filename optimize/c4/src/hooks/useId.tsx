/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import { useState } from "react"

export function useId() {
	const [id] = useState((Math.random() + 1).toString(36))

	return id
}
