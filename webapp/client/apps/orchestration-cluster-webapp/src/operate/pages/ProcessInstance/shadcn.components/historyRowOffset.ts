/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const HISTORY_ROW_ATTRIBUTE = 'data-history-row';

function historyRowProps(rowId: string) {
	return {[HISTORY_ROW_ATTRIBUTE]: rowId};
}

function historyRowOffset(scroller: HTMLElement | null, rowId: string) {
	const element = scroller?.querySelector(`[${HISTORY_ROW_ATTRIBUTE}="${CSS.escape(rowId)}"]`);
	return scroller && element ? element.getBoundingClientRect().top - scroller.getBoundingClientRect().top : undefined;
}

export {historyRowOffset, historyRowProps};
