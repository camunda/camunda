/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef, useState, useEffect} from 'react';
import * as Styled from './styled';

type Props = {
  label?: string;
  type?: 'info' | 'warning' | 'skeleton';
  rowHeight?: number;
  Skeleton?: React.FC;
};

export default function EmptyPanel({
  type,
  label,
  rowHeight,
  Skeleton,
  ...props
}: Props) {
  const containerRef = useRef(null);

  return (
    <Styled.EmptyPanel {...props} ref={containerRef}>
      {type === 'skeleton' && Skeleton !== undefined ? (
        <WithRowCount rowHeight={rowHeight} containerRef={containerRef}>
          <Skeleton />
        </WithRowCount>
      ) : (
        <Styled.LabelContainer>
          {type === 'warning' && (
            <Styled.WarningIcon data-testid="warning-icon" />
          )}
          {/* @ts-expect-error ts-migrate(2769) FIXME: Property 'type' does not exist on type 'IntrinsicA... Remove this comment to see the full error message */}
          <Styled.Label type={type}>{label}</Styled.Label>
        </Styled.LabelContainer>
      )}
    </Styled.EmptyPanel>
  );
}

/**
 * @returns {number} the number of rows a skelton should show, based on the available screen height and the row height of the data expected to be shown
 * @param {number} rowHeight row height of the data expected to be shown
 * @param {obj} containerRef ref of the parent component
 */
export const WithRowCount = function ({
  rowHeight,
  containerRef,
  ...props
}: any) {
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function renderChildren() {
    return React.Children.map(
      props.children,
      (child) =>
        child &&
        React.cloneElement(child, {
          rowsToDisplay: rows,
        })
    );
  }

  return renderChildren();
};
