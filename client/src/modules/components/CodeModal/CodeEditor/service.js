/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import sanitizeHtml from 'sanitize-html';

export function destructurePasteEvent(event) {
  const content = event.clipboardData.getData('Text');
  const isNewLine = event.target.localName === 'br';
  const targetElement = isNewLine ? event.target.parentElement : event.target;
  return {content, targetElement};
}

export function getHtmlContent(HTMLstring) {
  return sanitizeHtml(HTMLstring, {
    allowedTags: []
  });
}

export function getLocalCaretPosition() {
  const sel = window.getSelection();
  const doesRangeExist = sel.rangeCount !== 0;
  return doesRangeExist ? sel.getRangeAt(0).startOffset : 0;
}

export function getCaretPosition(prevHTML, elementIndex) {
  const sel = window.getSelection();
  const doesRangeExist = sel.rangeCount !== 0;
  const localCaretPosition = doesRangeExist ? sel.getRangeAt(0).startOffset : 0;
  let globalCharsBeforeCaret = 0;
  let currentElementIndex = 0;

  sanitizeHtml(prevHTML, {
    exclusiveFilter: function({text}) {
      if (currentElementIndex === elementIndex) {
        return;
      }
      globalCharsBeforeCaret += text.length;
      currentElementIndex++;
    }
  });

  return globalCharsBeforeCaret + localCaretPosition;
}

export function setBasicCaret(el, targetElementIndex, newCaretPosition) {
  const range = document.createRange();
  const sel = window.getSelection();
  sel.removeAllRanges();

  const element = el.current.children[targetElementIndex];

  if (!element) {
    return;
  }

  // element's first child is the textcontent, if no next -> default caret position
  let {targetElement, position} = element.firstChild
    ? {targetElement: element.firstChild, position: newCaretPosition}
    : {targetElement: element, position: 0};

  // set caret at correct or maximal possible position
  if (targetElement.length < position) {
    position = targetElement.length;
  }

  range.setStart(targetElement, position);
  range.setEnd(targetElement, position);
  sel.addRange(range);
}
