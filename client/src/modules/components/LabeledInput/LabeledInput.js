import React from 'react';

import {Labeled, Input} from 'components';
import classnames from 'classnames';

export default function LabeledInput({label, className, children, ...props}) {
  return (
    <div className={classnames('LabeledInput', className)}>
      <Labeled id={props.id} label={label}>
        <Input {...props} />
      </Labeled>
      {children}
    </div>
  );
}
