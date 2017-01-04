import {jsx, Text} from 'view-utils';

const template = <footer className="cam-brand-footer">
  <div className="container-fluid">
    <div className="col-xs-8">
    </div>
    <div className="text-right col-xs-4">
      &copy; Camunda services GmbH 2017, All Rights Reserved / <span className="version"><Text property="version" /></span>
    </div>
  </div>
</footer>;

export function Footer() {
  return template;
}
