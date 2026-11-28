/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {Heading, InlineCode, linkVariants} from '@camunda/design-system';

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
	<div className="flex flex-col gap-3">
		<ReactMarkdown
			remarkPlugins={REMARK_PLUGINS}
			allowedElements={ALLOWED_ELEMENTS}
			unwrapDisallowed
			components={{
				h1: ({children}) => (
					<Heading as="h3" variant="heading-xs" className="m-0 text-inherit">
						{children}
					</Heading>
				),
				h2: ({children}) => (
					<Heading as="h4" variant="heading-xs" className="m-0 text-inherit">
						{children}
					</Heading>
				),
				h3: ({children}) => (
					<Heading as="h5" variant="heading-xs" className="m-0 text-inherit">
						{children}
					</Heading>
				),
				h4: ({children}) => (
					<Heading as="h6" variant="heading-xs" className="m-0 text-inherit">
						{children}
					</Heading>
				),
				h5: ({children}) => (
					<Heading as="h6" variant="heading-xs" className="m-0 text-inherit">
						{children}
					</Heading>
				),
				h6: ({children}) => (
					<Heading as="h6" variant="heading-xs" className="m-0 text-inherit">
						{children}
					</Heading>
				),
				p: ({children}) => <p className="m-0 whitespace-pre-line text-inherit">{children}</p>,
				code: ({children, className}) => (className ? <code>{children}</code> : <InlineCode>{children}</InlineCode>),
				pre: ({children}) => (
					<pre className="m-0 overflow-x-auto whitespace-pre-wrap break-words rounded bg-[var(--neutral-background-strong)] p-3 font-mono text-[0.85em] [&_code]:bg-transparent [&_code]:p-0 [&_code]:text-inherit">
						{children}
					</pre>
				),
				ul: ({children}) => <ul className="m-0 list-disc pl-5">{children}</ul>,
				ol: ({children, start}) => (
					<ol start={start} className="m-0 list-decimal pl-5">
						{children}
					</ol>
				),
				blockquote: ({children}) => (
					<blockquote className="m-0 border-l-[3px] border-border px-4 py-2 text-[var(--neutral-foreground-subtle)]">
						{children}
					</blockquote>
				),
				table: ({children}) => (
					<div className="overflow-x-auto">
						<table className="w-full border-collapse text-[0.85em] [&_td]:border [&_td]:border-[var(--border)] [&_td]:px-3 [&_td]:py-2 [&_td]:text-left [&_th]:border [&_th]:border-[var(--border)] [&_th]:bg-[var(--neutral-background-strong)] [&_th]:px-3 [&_th]:py-2 [&_th]:text-left [&_th]:font-semibold">
							{children}
						</table>
					</div>
				),
				a: ({children, href}) =>
					href === undefined ? (
						<span>{children}</span>
					) : (
						<a href={href} target="_blank" rel="noopener noreferrer" className={linkVariants()}>
							{children}
						</a>
					),
			}}
		>
			{content}
		</ReactMarkdown>
	</div>
);

export {MarkdownMessage};
