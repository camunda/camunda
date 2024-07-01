/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useRef} from 'react';

export default function DiagramScrollLock() {
  const referenceNode = useRef();

  useEffect(() => {
    const visualization = referenceNode?.current.parentNode.querySelector('.BPMNDiagram');

    function stopEvent(evt) {
      evt.stopPropagation();
    }

    if (visualization) {
      visualization.addEventListener('wheel', stopEvent, true);
    }
    return () => {
      if (visualization) {
        visualization.removeEventListener('wheel', stopEvent, true);
      }
    };
  }, [referenceNode]);

  return <div className="DiagramScrollLock" ref={referenceNode} />;
}
