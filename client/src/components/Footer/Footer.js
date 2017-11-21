import React from 'react';
import './Footer.css';

import {getImportProgress} from './service';


export default class Footer extends React.Component {

  constructor(props) {
    super(props);

    this.state = {
      importProgress: null,
    };
    this.loadImportProgress();
  }

  loadImportProgress = async () => {
    let response = await getImportProgress();
    let importProgress = response.progress;

    this.setState({importProgress});
  }


  componentDidMount() {
    setInterval(() => {
      this.loadImportProgress();
    }, 5000);
  }



  render() {
    return (
      <footer className='Footer'>
        <span className='import-progress-footer'>Import progress is {this.state.importProgress}%  </span>
        © Camunda Services GmbH 2017, All Rights Reserved. | {this.props.version}
      </footer>
    );
  }
}

