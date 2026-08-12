/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LoginPage} from './Login.page';

class TasklistLoginPage extends LoginPage {
	override async goto(redirect?: string) {
		const search = redirect === undefined ? '' : `?${new URLSearchParams({redirect})}`;
		return this.page.goto(`/tasklist/login${search}`);
	}

	get genericErrorHeading() {
		return this.page.getByRole('heading', {name: 'Something went wrong'});
	}

	get title() {
		return this.page.getByRole('heading', {name: 'Tasklist'});
	}
}

export {TasklistLoginPage};
