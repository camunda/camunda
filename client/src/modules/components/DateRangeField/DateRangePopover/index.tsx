/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from 'modules/components/Button';
import {Footer, Popover} from './styled';

type Props = {
  referenceElement: HTMLElement;
  onCancel: () => void;
  onApply: () => void;
};

const DateRangePopover: React.FC<Props> = ({
  referenceElement,
  onCancel,
  onApply,
}) => {
  return (
    <Popover
      referenceElement={referenceElement}
      offsetOptions={{offset: [0, 10]}}
      placement="right"
      onOutsideClick={onCancel}
    >
      <Footer>
        <Button
          color="secondary"
          size="medium"
          title="Cancel"
          onClick={onCancel}
        >
          Cancel
        </Button>
        <Button onClick={onApply} color="primary" size="medium" title="Apply">
          Apply
        </Button>
      </Footer>
    </Popover>
  );
};

export {DateRangePopover};
