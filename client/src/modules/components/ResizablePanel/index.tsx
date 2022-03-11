/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import './index.css';
import Splitter, {SplitDirection} from '@devbookhq/splitter';

type Props = {
  direction: SplitDirection.Vertical | SplitDirection.Horizontal;
  initialSizePercentages: number[];
  minHeights?: number[];
  minWidths?: number[];
};

const ResizablePanel: React.FC<Props> = ({
  children,
  direction,
  initialSizePercentages,
  minHeights,
  minWidths,
}) => {
  const cursorResizingClassName =
    direction === SplitDirection.Vertical ? 'nsResizing' : 'ewResizing';

  return (
    <Splitter
      classes={[`${direction}Panel`, `${direction}Panel`]}
      direction={direction}
      minHeights={minHeights}
      minWidths={minWidths}
      initialSizes={initialSizePercentages}
      gutterClassName={`custom-gutter-${direction}`}
      draggerClassName={`custom-dragger-${direction}`}
      onResizeStarted={() => {
        document.body.classList.add(cursorResizingClassName);
      }}
      onResizeFinished={() => {
        document.body.classList.remove(cursorResizingClassName);
      }}
    >
      {children}
    </Splitter>
  );
};

export {ResizablePanel, SplitDirection};
