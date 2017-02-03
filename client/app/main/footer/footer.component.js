import {jsx, Text} from 'view-utils';

const template = <footer cam-widget-footer="">
  <div className="container-fluid">
    <div className="row">
      <div className="col-xs-12">
        &copy; Camunda services GmbH 2017, All Rights Reserved / <span className="version"><Text property="version" /></span>
      </div>
    </div>
  </div>
</footer>;

export function Footer() {
  return template;
}
