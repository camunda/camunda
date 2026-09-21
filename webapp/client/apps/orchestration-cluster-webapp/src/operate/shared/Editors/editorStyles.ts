/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {createGlobalStyle} from 'styled-components';

const EditorStyles = createGlobalStyle`
	.monaco-editor textarea:focus { box-shadow: none !important; }
`;
const EditorWrapper = styled.div<{$invalid?: boolean}>`
	position: relative;
	min-width: 0;
	${({$invalid}) =>
		$invalid &&
		`
		.cds--form-requirement {
			display: block;
			overflow: visible;
			max-block-size: none;
			color: var(--cds-text-error);
		}
	`}
`;
const ReadOnlyWrapper = styled.div<{$invalid: boolean; $empty: boolean}>`
	position: relative;
	width: 100%;
	min-height: 32px;
	color: ${({$empty}) => ($empty ? 'var(--cds-text-placeholder)' : 'inherit')};
	outline: ${({$invalid}) => ($invalid ? '2px solid var(--cds-support-error)' : 'none')};
	outline-offset: -2px;
	> svg {
		position: absolute;
		top: var(--cds-spacing-02);
		right: var(--cds-spacing-04);
		pointer-events: none;
		opacity: 0;
	}
	&:hover > svg,
	&:focus-within > svg {
		opacity: 1;
	}
`;
const ReadOnlyContent = styled.pre<{$height: number}>`
	max-height: ${({$height}) => $height}px;
	overflow-y: auto;
	font:
		13px/20px 'IBM Plex Mono',
		'Droid Sans Mono',
		monospace;
	padding-block: var(--cds-spacing-02);
	tab-size: 2;
	text-wrap: wrap;
	overflow-wrap: anywhere;
	cursor: pointer;
	&:focus-visible {
		outline: 2px solid var(--cds-focus);
		outline-offset: -2px;
	}
`;
const WriteModeEditor = styled.div<{$invalid: boolean}>`
	position: relative;
	border-block-end: 1px solid var(--cds-border-strong);
	&:focus-within {
		outline: 2px solid var(--cds-focus);
		outline-offset: -2px;
	}
	${({$invalid}) =>
		$invalid &&
		`
		&, &:focus-within { outline: 2px solid var(--cds-support-error); outline-offset: -2px; }
	`}
`;
const Toolbar = styled.div`
	display: flex;
	justify-content: flex-end;
	padding-bottom: var(--cds-spacing-03);
`;

export {EditorStyles, EditorWrapper, ReadOnlyWrapper, ReadOnlyContent, WriteModeEditor, Toolbar};
