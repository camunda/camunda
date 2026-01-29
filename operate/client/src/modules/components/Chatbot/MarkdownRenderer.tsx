/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useMemo} from 'react';
import styled from 'styled-components';

/**
 * Simple Markdown renderer for chat messages.
 * Handles common markdown patterns without external dependencies.
 *
 * Supported:
 * - **bold** and *italic*
 * - `inline code`
 * - ```code blocks``` with syntax highlighting
 * - - bullet lists
 * - 1. numbered lists
 * - [links](url)
 * - > blockquotes
 * - Headers (# ## ###)
 */

const MarkdownContainer = styled.div`
  line-height: 1.5;
  word-break: break-word;

  p {
    margin: 0 0 0.5em 0;

    &:last-child {
      margin-bottom: 0;
    }
  }

  strong {
    font-weight: 600;
  }

  em {
    font-style: italic;
  }

  code {
    background: var(--cds-field, rgba(0, 0, 0, 0.1));
    padding: 0.1em 0.4em;
    border-radius: 4px;
    font-family: 'IBM Plex Mono', 'Menlo', 'Monaco', monospace;
    font-size: 0.9em;
  }

  pre {
    background: var(--cds-field, #262626);
    color: var(--cds-text-primary, #f4f4f4);
    padding: var(--cds-spacing-04, 1rem);
    border-radius: 6px;
    overflow-x: auto;
    margin: 0.5em 0;
    font-family: 'IBM Plex Mono', 'Menlo', 'Monaco', monospace;
    font-size: 0.85em;
    line-height: 1.4;

    code {
      background: none;
      padding: 0;
      font-size: inherit;
    }
  }

  ul, ol {
    margin: 0.5em 0;
    padding-left: 1.5em;
  }

  li {
    margin: 0.25em 0;
  }

  blockquote {
    border-left: 3px solid var(--cds-border-subtle, #525252);
    padding-left: var(--cds-spacing-04, 1rem);
    margin: 0.5em 0;
    color: var(--cds-text-secondary, #c6c6c6);
    font-style: italic;
  }

  a {
    color: var(--cds-link-primary, #78a9ff);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  h1, h2, h3, h4 {
    margin: 0.75em 0 0.5em 0;
    font-weight: 600;
    line-height: 1.3;
  }

  h1 { font-size: 1.4em; }
  h2 { font-size: 1.25em; }
  h3 { font-size: 1.1em; }
  h4 { font-size: 1em; }

  hr {
    border: none;
    border-top: 1px solid var(--cds-border-subtle, #525252);
    margin: 1em 0;
  }

  table {
    border-collapse: collapse;
    margin: 0.5em 0;
    width: 100%;
    font-size: 0.9em;
  }

  th, td {
    border: 1px solid var(--cds-border-subtle, #525252);
    padding: 0.5em;
    text-align: left;
  }

  th {
    background: var(--cds-layer-accent, #393939);
    font-weight: 600;
  }
`;

// Syntax highlighting colors (simple approach)
const SyntaxHighlight = styled.span<{$type: string}>`
  ${({$type}) => {
    switch ($type) {
      case 'keyword':
        return 'color: #ff7b72;'; // red
      case 'string':
        return 'color: #a5d6ff;'; // light blue
      case 'number':
        return 'color: #79c0ff;'; // blue
      case 'comment':
        return 'color: #8b949e; font-style: italic;'; // gray
      case 'function':
        return 'color: #d2a8ff;'; // purple
      case 'property':
        return 'color: #7ee787;'; // green
      default:
        return '';
    }
  }}
`;

// Simple syntax highlighting for common languages
function highlightCode(code: string, language?: string): React.ReactNode {
  if (!language || !['javascript', 'typescript', 'js', 'ts', 'json', 'java', 'python', 'py'].includes(language.toLowerCase())) {
    return code;
  }

  const strings = /(["'`])(?:(?!\1)[^\\]|\\.)*?\1/g;
  const comments = /(\/\/.*$|\/\*[\s\S]*?\*\/)/gm;

  // Split by patterns and reassemble with highlighting
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let key = 0;

  // Simple tokenization - just highlight strings and keywords
  const tokens: Array<{start: number; end: number; type: string; text: string}> = [];

  // Find strings first (they take precedence)
  let match;
  while ((match = strings.exec(code)) !== null) {
    tokens.push({start: match.index, end: match.index + match[0].length, type: 'string', text: match[0]});
  }

  // Find comments
  while ((match = comments.exec(code)) !== null) {
    tokens.push({start: match.index, end: match.index + match[0].length, type: 'comment', text: match[0]});
  }

  // Sort tokens by start position
  tokens.sort((a, b) => a.start - b.start);

  // Remove overlapping tokens
  const filteredTokens: typeof tokens = [];
  for (const token of tokens) {
    const lastToken = filteredTokens[filteredTokens.length - 1];
    if (!lastToken || token.start >= lastToken.end) {
      filteredTokens.push(token);
    }
  }

  // Build result
  for (const token of filteredTokens) {
    if (token.start > lastIndex) {
      // Add unhighlighted text between tokens
      const text = code.slice(lastIndex, token.start);
      // Highlight keywords in unhighlighted sections
      parts.push(highlightKeywords(text, key++));
    }
    parts.push(
      <SyntaxHighlight key={key++} $type={token.type}>
        {token.text}
      </SyntaxHighlight>
    );
    lastIndex = token.end;
  }

  // Add remaining text
  if (lastIndex < code.length) {
    parts.push(highlightKeywords(code.slice(lastIndex), key++));
  }

  return parts.length > 0 ? parts : code;
}

function highlightKeywords(text: string, baseKey: number): React.ReactNode {
  const keywords = /\b(const|let|var|function|class|if|else|for|while|return|import|export|from|async|await|try|catch|throw|new|this|true|false|null|undefined|public|private|static|void|int|String|boolean)\b/g;
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let key = 0;
  let match;

  while ((match = keywords.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index));
    }
    parts.push(
      <SyntaxHighlight key={`${baseKey}-${key++}`} $type="keyword">
        {match[0]}
      </SyntaxHighlight>
    );
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }

  return parts.length > 0 ? <>{parts}</> : text;
}

// Parse markdown to React elements
function parseMarkdown(text: string): React.ReactNode {
  const lines = text.split('\n');
  const elements: React.ReactNode[] = [];
  let key = 0;
  let inCodeBlock = false;
  let codeBlockContent: string[] = [];
  let codeBlockLanguage = '';
  let inList = false;
  let listItems: React.ReactNode[] = [];
  let listType: 'ul' | 'ol' = 'ul';

  const flushList = () => {
    if (listItems.length > 0) {
      if (listType === 'ul') {
        elements.push(<ul key={key++}>{listItems}</ul>);
      } else {
        elements.push(<ol key={key++}>{listItems}</ol>);
      }
      listItems = [];
      inList = false;
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Code block start/end
    if (line.startsWith('```')) {
      if (inCodeBlock) {
        // End code block
        elements.push(
          <pre key={key++}>
            <code>{highlightCode(codeBlockContent.join('\n'), codeBlockLanguage)}</code>
          </pre>
        );
        codeBlockContent = [];
        codeBlockLanguage = '';
        inCodeBlock = false;
      } else {
        // Start code block
        flushList();
        inCodeBlock = true;
        codeBlockLanguage = line.slice(3).trim();
      }
      continue;
    }

    if (inCodeBlock) {
      codeBlockContent.push(line);
      continue;
    }

    // Empty line
    if (line.trim() === '') {
      flushList();
      continue;
    }

    // Headers
    const headerMatch = line.match(/^(#{1,4})\s+(.+)$/);
    if (headerMatch) {
      flushList();
      const level = headerMatch[1].length;
      const content = parseInline(headerMatch[2]);
      switch (level) {
        case 1:
          elements.push(<h1 key={key++}>{content}</h1>);
          break;
        case 2:
          elements.push(<h2 key={key++}>{content}</h2>);
          break;
        case 3:
          elements.push(<h3 key={key++}>{content}</h3>);
          break;
        default:
          elements.push(<h4 key={key++}>{content}</h4>);
      }
      continue;
    }

    // Blockquote
    if (line.startsWith('> ')) {
      flushList();
      elements.push(<blockquote key={key++}>{parseInline(line.slice(2))}</blockquote>);
      continue;
    }

    // Horizontal rule
    if (line.match(/^(-{3,}|\*{3,}|_{3,})$/)) {
      flushList();
      elements.push(<hr key={key++} />);
      continue;
    }

    // Unordered list
    const ulMatch = line.match(/^[-*+]\s+(.+)$/);
    if (ulMatch) {
      if (!inList || listType !== 'ul') {
        flushList();
        inList = true;
        listType = 'ul';
      }
      listItems.push(<li key={key++}>{parseInline(ulMatch[1])}</li>);
      continue;
    }

    // Ordered list
    const olMatch = line.match(/^\d+\.\s+(.+)$/);
    if (olMatch) {
      if (!inList || listType !== 'ol') {
        flushList();
        inList = true;
        listType = 'ol';
      }
      listItems.push(<li key={key++}>{parseInline(olMatch[1])}</li>);
      continue;
    }

    // Regular paragraph
    flushList();
    elements.push(<p key={key++}>{parseInline(line)}</p>);
  }

  // Flush any remaining code block
  if (inCodeBlock && codeBlockContent.length > 0) {
    elements.push(
      <pre key={key++}>
        <code>{highlightCode(codeBlockContent.join('\n'), codeBlockLanguage)}</code>
      </pre>
    );
  }

  flushList();

  return elements;
}

// Parse inline markdown (bold, italic, code, links)
function parseInline(text: string): React.ReactNode {
  const parts: React.ReactNode[] = [];
  let remaining = text;
  let key = 0;

  while (remaining.length > 0) {
    // Inline code (must come before bold/italic to handle backticks correctly)
    const codeMatch = remaining.match(/^`([^`]+)`/);
    if (codeMatch) {
      parts.push(<code key={key++}>{codeMatch[1]}</code>);
      remaining = remaining.slice(codeMatch[0].length);
      continue;
    }

    // Bold
    const boldMatch = remaining.match(/^\*\*(.+?)\*\*/);
    if (boldMatch) {
      parts.push(<strong key={key++}>{boldMatch[1]}</strong>);
      remaining = remaining.slice(boldMatch[0].length);
      continue;
    }

    // Italic
    const italicMatch = remaining.match(/^\*(.+?)\*/);
    if (italicMatch) {
      parts.push(<em key={key++}>{italicMatch[1]}</em>);
      remaining = remaining.slice(italicMatch[0].length);
      continue;
    }

    // Links
    const linkMatch = remaining.match(/^\[([^\]]+)\]\(([^)]+)\)/);
    if (linkMatch) {
      parts.push(
        <a key={key++} href={linkMatch[2]} target="_blank" rel="noopener noreferrer">
          {linkMatch[1]}
        </a>
      );
      remaining = remaining.slice(linkMatch[0].length);
      continue;
    }

    // Regular character
    const nextSpecial = remaining.search(/[`*\[]/);
    if (nextSpecial === -1) {
      parts.push(remaining);
      break;
    } else if (nextSpecial === 0) {
      // Special char that didn't match a pattern, treat as regular text
      parts.push(remaining[0]);
      remaining = remaining.slice(1);
    } else {
      parts.push(remaining.slice(0, nextSpecial));
      remaining = remaining.slice(nextSpecial);
    }
  }

  return parts.length === 1 ? parts[0] : <>{parts}</>;
}

type MarkdownRendererProps = {
  content: string;
  className?: string;
};

const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({content, className}) => {
  const rendered = useMemo(() => parseMarkdown(content), [content]);

  return <MarkdownContainer className={className}>{rendered}</MarkdownContainer>;
};

export {MarkdownRenderer};
