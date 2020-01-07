/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useState, useEffect} from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

export default function EmptyPanel({
  type,
  label,
  rowHeight,
  Skeleton,
  ...props
}) {
  const containerRef = useRef(null);

  return (
    <Styled.EmptyPanel {...props} ref={containerRef}>
      {type === 'skeleton' ? (
        <WithRowCount rowHeight={rowHeight} containerRef={containerRef}>
          <Skeleton />
        </WithRowCount>
      ) : (
        <Styled.LabelContainer>
          {type === 'warning' && <Styled.WarningIcon />}
          <Styled.Label type={type}>{label}</Styled.Label>
        </Styled.LabelContainer>
      )}
    </Styled.EmptyPanel>
  );
}

EmptyPanel.propTypes = {
  label: PropTypes.string,
  skeleton: PropTypes.object,
  type: PropTypes.oneOf(['info', 'warning', 'skeleton'])
};

/**
 * @returns {number} the number of rows a skelton should show, based on the available screen height and the row height of the data expected to be shown
 * @param {number} rowHeight row height of the data expected to be shown
 * @param {obj} containerRef ref of the parent component
 */
export const WithRowCount = function({rowHeight, containerRef, ...props}) {
  const [rows, setRows] = useState(0);

  useEffect(() => {
    function recalculateHeight() {
      if (containerRef.current) {
        const rows = Math.floor(containerRef.current.clientHeight / rowHeight);

        setRows(rows);
      }
    }

    recalculateHeight();

    window.addEventListener('resize', recalculateHeight);
    return () => window.removeEventListener('resize', recalculateHeight);
  }, []);

  function renderChildren() {
    return React.Children.map(
      props.children,
      child =>
        child &&
        React.cloneElement(child, {
          rowsToDisplay: rows
        })
    );
  }

  return renderChildren();
};
