/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const MARKDOWN_PATTERNS = [
  /(?:^|\n)#{1,6}\s/, // headings
  /\*\*.+?\*\*/, // bold
  /(?:^|\s)_[^\s_].*?[^\s_]_(?:\s|$)/, // italic
  /~~.+?~~/, // strikethrough
  /`.+?`/, // inline code
  /```[\s\S]*?```/, // fenced code blocks
  /(?:^|\n)\s*[-*+]\s/, // unordered lists
  /(?:^|\n)\s*\d+\.\s/, // ordered lists
  /\[.+?\]\(.+?\)/, // links
  /(?:^|\n)>\s/, // blockquotes
  /(?:^|\n)\|.+\|/, // tables
];

function containsMarkdown(text: string): boolean {
  return MARKDOWN_PATTERNS.some((pattern) => pattern.test(text));
}

export {containsMarkdown};
