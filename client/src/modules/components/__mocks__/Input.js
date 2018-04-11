// we could use this if enzyme fixed https://github.com/airbnb/enzyme/issues/1604
// export {Input as default} from '../Input';

import React from 'react';

class Input extends React.Component {
  render() {
    const {props} = this;
    return (
      <input
        id={props.id}
        readOnly={props.readOnly}
        type={props.type}
        onChange={props.onChange}
        value={props.value}
        name={props.name}
        className={props.className}
      />
    );
  }
}

export default Input;
