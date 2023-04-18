/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export function getHighlightedText(
  text: string,
  highlight: string,
  matchFromStart?: boolean
): JSX.Element[] | string {
  if (!highlight) {
    return text;
  }

  // we need to escape special characters in the highlight text
  // https://stackoverflow.com/a/3561711
  let regex = highlight.replace(/[-\\^$*+?.()|[\]{}]/g, '\\$&');
  if (matchFromStart) {
    regex = '^' + regex;
  }

  // Split on highlight term and include term into parts, ignore case
  const parts = text.split(new RegExp(`(${regex})`, 'gi'));

  return parts.map((part, i) => (
    <span
      key={i}
      className={part.toLowerCase() === highlight.toLowerCase() ? 'textBold' : undefined}
    >
      {part}
    </span>
  ));
}
