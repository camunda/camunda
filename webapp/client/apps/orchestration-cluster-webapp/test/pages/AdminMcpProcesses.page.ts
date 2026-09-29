/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BasePage} from './BasePage';

class AdminMcpProcessesPage extends BasePage {
	async goto() {
		return this.page.goto('/admin/mcp-processes');
	}

	get heading() {
		return this.page.getByRole('heading', {name: 'MCP Processes', exact: true});
	}

	get searchField() {
		return this.page.getByRole('searchbox');
	}

	get table() {
		return this.page.getByRole('table', {name: 'MCP Processes'});
	}

	get toolNameSortButton() {
		return this.page.getByRole('button', {name: 'Tool name'});
	}

	get pageSizeSelect() {
		return this.page.getByRole('combobox');
	}

	get nextPageButton() {
		return this.page.getByRole('button', {name: 'Next page'});
	}

	columnHeader(name: string) {
		return this.table.locator('th').filter({hasText: name});
	}

	row(toolName: string) {
		return this.table.getByRole('row').filter({hasText: toolName});
	}

	cell(text: string) {
		return this.table.getByRole('cell', {name: text, exact: true});
	}

	expandToggle(toolName: string) {
		return this.row(toolName).getByRole('button').first();
	}

	detailHeading(name: string) {
		return this.page.getByRole('heading', {name, exact: true});
	}

	detailText(text: string) {
		return this.page.getByText(text);
	}

	get loadFailureHeading() {
		return this.page.getByRole('heading', {name: 'Something went wrong'});
	}
}

export {AdminMcpProcessesPage};
