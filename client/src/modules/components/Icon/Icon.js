import React from 'react';

import './Icon.css';
import icons from './icons';

export default function Icon(props) {
  const filteredProps = {...props};
  const type = props.type || 'plus';
  const Tag = props.renderedIn || 'span';
  delete filteredProps.renderedIn;
  delete filteredProps.type;

  if (props.renderedIn) {
    return <Tag {...filteredProps} className={'Icon Icon--' + type} />;
  } else {
    const SVG = icons[type];

    return (
      <span className="Icon Icon--svg">
        <SVG />
      </span>
    );
  }
}
