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

import {createBeautyfiedJSON} from 'modules/utils/variable';

const codeElementRef = React.createRef();

function CodeEditor({contentEditable, initialValue}) {
  const [codeHTML, setCodeHTML] = useState(returnCodeLine());

  // Set initial Code modal content
  useEffect(() => {
    setCodeHTML(
      renderCodeLines(
        isValidJSON(initialValue)
          ? createBeautyfiedJSON(initialValue, 2)
          : initialValue
      )
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  return (
    <Styled.CodeEditor>
      <Styled.Pre>
        <ContentEditable
          tagName="code"
          html={codeHTML}
          innerRef={codeElementRef}
          disabled={!contentEditable}
        />
      </Styled.Pre>
    </Styled.CodeEditor>
  );
}
export default CodeEditor;

CodeEditor.propTypes = {
  contentEditable: PropTypes.bool,
  initialValue: PropTypes.string
};
