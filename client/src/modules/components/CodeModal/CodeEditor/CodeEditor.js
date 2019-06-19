/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import ContentEditable from 'react-contenteditable';

import PropTypes from 'prop-types';

import * as Styled from './styled';

import {isValidJSON} from 'modules/utils';

import {
  getCaretPosition,
  setBasicCaret,
  getHtmlContent,
  destructurePasteEvent,
  getLocalCaretPosition
} from './service';
import {
  createBeautyfiedJSON,
  removeWhiteSpaces,
  removeLineBreaks
} from '../service';

const codeElementRef = React.createRef();

function CodeEditor({contentEditable, initialValue, handleChange}) {
  const [codeHTML, setCodeHTML] = useState(returnCodeLine());
  const [currentCaret, setCurrentCaret] = useState({});

  // Set initial Code modal content
  useEffect(() => {
    setCodeHTML(
      renderCodeLines(
        isValidJSON(initialValue)
          ? createBeautyfiedJSON(initialValue, 2)
          : initialValue
      )
    );
  }, []);

  useEffect(() => {
    if (Object.keys(currentCaret).length > 0) {
      console.log('set caret', currentCaret.localCaretPosition);
      setBasicCaret(
        codeElementRef,
        currentCaret.elementIndex,
        currentCaret.localCaretPosition
      );
    }
  });

  // update parent with sanitized content when content changed,
  // layout changes are ignored.
  useEffect(
    () => {
      const {textContent} = codeElementRef.current;
      handleChange(
        isValidJSON(textContent)
          ? removeWhiteSpaces(removeLineBreaks(textContent))
          : ''
      );
    },
    [codeHTML]
  );

  function returnCodeLine(content = '') {
    return `<p className="code-line">${content}</p>`;
  }

  function renderCodeLines(content) {
    let htmlString = '';
    content.split('\n').forEach(lineContent => {
      htmlString = htmlString + returnCodeLine(lineContent);
    });
    return htmlString;
  }

  function handleOnChange(newHTML) {
    if (getLocalCaretPosition() === currentCaret.localCaretPosition) {
      setCurrentCaret({});
    }

    setCodeHTML(!getHtmlContent(newHTML) ? returnCodeLine() : newHTML);
  }

  function beautifyString(stingValue) {
    return isValidJSON(stingValue)
      ? createBeautyfiedJSON(stingValue, 2)
      : stingValue;
  }

  function handleOnPaste(event) {
    event.stopPropagation();
    event.preventDefault();
    const {innerHTML, children: codeLines} = codeElementRef.current;
    const {content: pastedContent, targetElement} = destructurePasteEvent(
      event
    );

    const elementIndex = [...codeLines].indexOf(targetElement);
    const caretPosition = getCaretPosition(codeHTML, elementIndex);

    const currentContent = getHtmlContent(innerHTML);
    const beautifiedPastedContent = beautifyString(pastedContent);

    const newContent = beautifyString(
      currentContent.slice(0, caretPosition) +
        beautifiedPastedContent +
        currentContent.slice(caretPosition)
    );

    setCodeHTML(renderCodeLines(newContent));

    const currentCaret = beautifiedPastedContent.includes('\n')
      ? {
          // if multi line
          elementIndex: 0,
          localCaretPosition: 0
        }
      : {
          // if inline
          elementIndex: elementIndex,
          localCaretPosition: getLocalCaretPosition() + pastedContent.length
        };

    setCurrentCaret(currentCaret);
  }

  return (
    <Styled.CodeEditor>
      <Styled.Pre>
        <ContentEditable
          tagName="code"
          html={codeHTML}
          innerRef={codeElementRef}
          disabled={!contentEditable}
          onChange={e => handleOnChange(e.target.value)}
          onPaste={e => handleOnPaste(e)}
        />
      </Styled.Pre>
    </Styled.CodeEditor>
  );
}
export default CodeEditor;

CodeEditor.propTypes = {
  contentEditable: PropTypes.bool,
  initialValue: PropTypes.string,
  handleChange: PropTypes.func
};
