import {jsx, Text, withSelector} from 'view-utils';
import {Progress} from './progress';

export const Footer = withSelector(() => <footer>
  <div className="container-fluid">
    <div className="row">
      <div className="col-xs-2">
        <Progress selector="progress" />
      </div>
      <div className="col-xs-10">
        &copy; Camunda services GmbH 2017, All Rights Reserved / <span className="version"><Text property={() => process.env.version} /></span>
      </div>
    </div>
  </div>
</footer>);
