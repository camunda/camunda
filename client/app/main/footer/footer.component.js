import {jsx, Text} from 'view-utils';

const template = <footer className="footer">
  <div>Camunda Optimize Footer</div>
  <div className="footer__version"><Text property="version" /></div>
</footer>;

export function Footer() {
  return template;
}
