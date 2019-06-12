/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState, useRef} from 'react';
import ContentEditable from 'react-contenteditable';

import PropTypes from 'prop-types';

import * as Styled from './styled';

import {isValidJSON} from 'modules/utils';
import {
  createBeautyfiedJSON,
  removeWhiteSpaces,
  removeLineBreaks
} from '../service';

const ref = React.createRef();
const defaultContent =
  '<p className="code-line" data-test="codeline-0" key=0 ></p>';

function CodeEditor({contentEditable, initialValue, handleChange}) {
  const [codeHTML, setCodeHTML] = useState(defaultContent);

  const prevContent = usePrevious(ref);

  function usePrevious(codeRef) {
    if (!codeRef) {
      return '';
    }

    const ref = useRef();
    useEffect(() => {
      ref.current = codeRef.current.textContent;
    });
    return ref.current;
  }

  // Set initial Code modal content
  useEffect(
    () => {
      setCodeHTML(
        renderCodeLines(
          isValidJSON(initialValue)
            ? createBeautyfiedJSON(initialValue, 2)
            : initialValue
        )
      );
    },
    [initialValue]
  );

  // update parent with sanitized content when content changed,
  // layout changes are ignored.
  useEffect(
    () => {
      const {textContent} = ref.current;
      if (prevContent !== textContent) {
        handleChange(
          isValidJSON(textContent)
            ? removeWhiteSpaces(removeLineBreaks(textContent))
            : ''
        );
      }
    },
    [codeHTML]
  );

  function renderCodeLines(initialValue) {
    let htmlString = '';
    initialValue.split('\n').forEach((line, idx) => {
      htmlString =
        htmlString +
        `<p className="code-line" data-test="codeline-${idx}" key=${idx}>${line}</p>`;
    });
    return htmlString;
  }

  function handleOnChange(newValue) {
    setCodeHTML(!newValue ? defaultContent : newValue);
  }

  return (
    <Styled.CodeEditor>
      <Styled.Pre>
        <ContentEditable
          innerRef={ref}
          html={codeHTML}
          disabled={!contentEditable}
          onChange={e => handleOnChange(e.target.value)}
          tagName="code"
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
