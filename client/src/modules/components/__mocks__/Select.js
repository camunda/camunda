import React from 'react';

const Select = props => <select {...props}>{props.children}</select>;
Select.Option = props => <option {...props}>{props.children}</option>;

export default Select;
