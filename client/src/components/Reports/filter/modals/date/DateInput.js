import React from 'react';
import moment from 'moment';
import classnames from 'classnames';
import {Input, ErrorMessage} from 'components';

import './DateInput.css';

class DateInput extends React.PureComponent {
  constructor(props) {
    super(props);

    this.state = {
      stringDate: this.props.date.format(this.props.format),
      error: false
    };
  }

  componentDidUpdate({date, format}) {
    const oldStringDate = date.format(format);
    const newStringDate = this.props.date.format(this.props.format);

    if (oldStringDate !== newStringDate) {
      this.setState({
        stringDate: newStringDate,
        error: false
      });
    }
  }

  render() {
    return (
      <div className="DateInput__input-group">
        <Input
          type="text"
          ref={this.props.reference}
          className={classnames(this.props.className, 'DateInput')}
          value={this.state.stringDate}
          onFocus={this.props.onFocus}
          onClick={this.onClick}
          onKeyDown={this.onKeyDown}
          onChange={this.onInputChange}
          isInvalid={this.state.error}
        />
        {this.state.error && (
          <ErrorMessage className="DateInput__warning">Please enter a valid date</ErrorMessage>
        )}
      </div>
    );
  }

  onClick = event => {
    // onClick property is optional, so there need to be safeguard
    if (typeof this.props.onClick === 'function') {
      this.props.onClick(event);
    }
  };

  onKeyDown = ({key}) => {
    if (key === 'Enter') {
      this.props.onSubmit();
    }
  };

  onInputChange = event => {
    const value = event.target.value;
    const date = moment(value, this.props.format);
    const isValid = date.isValid() && date.format(this.props.format) === value;

    this.setState({
      stringDate: value,
      error: !isValid
    });

    if (!date.isSame(this.props.date) && isValid) {
      this.props.onDateChange(date);
    }
    this.props.enableAddButton(isValid);
  };
}

export default React.forwardRef((props, ref) => <DateInput {...props} reference={ref} />);
