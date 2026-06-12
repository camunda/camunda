/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import styled from 'styled-components';

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

const MarkdownContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
`;

const Heading1 = styled.div`
  margin: 0;
  font-size: inherit;
  font-weight: var(--cds-heading-compact-02-font-weight);
`;

const Heading2 = styled.div`
  margin: 0;
  font-size: inherit;
  font-weight: var(--cds-heading-compact-02-font-weight);
`;

const Heading3 = styled.div`
  margin: 0;
  font-size: inherit;
  font-weight: var(--cds-heading-compact-01-font-weight);
`;

const InlineCode = styled.code`
  padding: var(--cds-spacing-01) var(--cds-spacing-02);
  border-radius: 4px;
  background-color: var(--cds-layer-accent-01);
  font-family: var(--cds-code-01-font-family);
  font-size: 0.85em;
`;

const CodeBlock = styled.pre`
  margin: 0;
  padding: var(--cds-spacing-03);
  border-radius: 4px;
  background-color: var(--cds-layer-accent-01);
  font-family: var(--cds-code-01-font-family);
  font-size: 0.85em;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;

  & > code {
    padding: 0;
    border-radius: 0;
    background-color: transparent;
  }
`;

const List = styled.ul`
  margin: 0;
  padding-left: var(--cds-spacing-05);
  list-style-type: disc;
`;

const OrderedList = styled.ol`
  margin: 0;
  padding-left: var(--cds-spacing-05);
  list-style-type: decimal;
`;

const Paragraph = styled.p`
  && {
    white-space: pre-line;
    margin: 0;
  }
`;

const Blockquote = styled.blockquote`
  margin: 0;
  padding: var(--cds-spacing-02) var(--cds-spacing-04);
  border-left: 3px solid var(--cds-border-subtle-01);
  color: var(--cds-text-secondary);
  font-size: inherit;
`;

const TableWrapper = styled.div`
  overflow-x: auto;
`;

const Table = styled.table`
  border-collapse: collapse;
  font-size: 0.85em;
  width: 100%;

  th,
  td {
    padding: var(--cds-spacing-02) var(--cds-spacing-03);
    border: 1px solid var(--cds-border-subtle-01);
    text-align: left;
  }

  th {
    font-weight: var(--cds-heading-compact-01-font-weight);
    background-color: var(--cds-layer-accent-01);
  }
`;

type MarkdownMessageProps = {
  content: string;
};

const MarkdownMessage: React.FC<MarkdownMessageProps> = ({content}) => {
  return (
    <MarkdownContainer>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        allowedElements={ALLOWED_ELEMENTS}
        unwrapDisallowed
        components={{
          h1: ({children}) => <Heading1>{children}</Heading1>,
          h2: ({children}) => <Heading2>{children}</Heading2>,
          h3: ({children}) => <Heading3>{children}</Heading3>,
          h4: ({children}) => <Heading3>{children}</Heading3>,
          h5: ({children}) => <Heading3>{children}</Heading3>,
          h6: ({children}) => <Heading3>{children}</Heading3>,
          p: ({children}) => <Paragraph>{children}</Paragraph>,
          code: ({children, className}) => {
            // Fenced code blocks get wrapped in <pre> by react-markdown,
            // and receive a className like "language-js"
            if (className) {
              return <code>{children}</code>;
            }
            return <InlineCode>{children}</InlineCode>;
          },
          pre: ({children}) => <CodeBlock>{children}</CodeBlock>,
          ul: ({children}) => <List>{children}</List>,
          ol: ({children}) => <OrderedList>{children}</OrderedList>,
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
};

export {MarkdownMessage};
