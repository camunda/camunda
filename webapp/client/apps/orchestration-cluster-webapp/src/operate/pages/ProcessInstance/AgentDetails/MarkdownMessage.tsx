/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
	Blockquote,
	CodeBlock,
	Heading1,
	Heading2,
	Heading3,
	Heading4,
	InlineCode,
	List,
	MarkdownContainer,
	OrderedList,
	Paragraph,
	Table,
	TableWrapper,
} from './markdownMessage.styled';

const ALLOWED_ELEMENTS = [
	'p',
	'strong',
	'em',
	'del',
	'code',
	'pre',
	'ul',
	'ol',
	'li',
	'a',
	'h1',
	'h2',
	'h3',
	'h4',
	'h5',
	'h6',
	'blockquote',
	'table',
	'thead',
	'tbody',
	'tr',
	'th',
	'td',
];

const REMARK_PLUGINS = [remarkGfm];

const MarkdownMessage: React.FC<{content: string}> = ({content}) => (
	<MarkdownContainer>
		<ReactMarkdown
			remarkPlugins={REMARK_PLUGINS}
			allowedElements={ALLOWED_ELEMENTS}
			unwrapDisallowed
			components={{
				h1: ({children}) => <Heading1>{children}</Heading1>,
				h2: ({children}) => <Heading2>{children}</Heading2>,
				h3: ({children}) => <Heading3>{children}</Heading3>,
				h4: ({children}) => <Heading4>{children}</Heading4>,
				h5: ({children}) => <Heading4>{children}</Heading4>,
				h6: ({children}) => <Heading4>{children}</Heading4>,
				p: ({children}) => <Paragraph>{children}</Paragraph>,
				code: ({children, className}) => (className ? <code>{children}</code> : <InlineCode>{children}</InlineCode>),
				pre: ({children}) => <CodeBlock>{children}</CodeBlock>,
				ul: ({children}) => <List>{children}</List>,
				ol: ({children, start}) => <OrderedList start={start}>{children}</OrderedList>,
				blockquote: ({children}) => <Blockquote>{children}</Blockquote>,
				table: ({children}) => (
					<TableWrapper>
						<Table>{children}</Table>
					</TableWrapper>
				),
				a: ({children, href}) => (
					<a href={href} target="_blank" rel="noopener noreferrer">
						{children}
					</a>
				),
			}}
		>
			{content}
		</ReactMarkdown>
	</MarkdownContainer>
);

export {MarkdownMessage};
