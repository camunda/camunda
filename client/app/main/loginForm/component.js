import {jsx, OnEvent, Match, Case, Default, withSelector, Attribute, isTruthy} from 'view-utils';
import {performLogin} from './service';

export const LoginForm = withSelector(Form);

function Form() {
  const template = <form className="form-horizontal form-signin">
    <OnEvent event="submit" listener={submit} />
    <h2>Login</h2>
    <div className="form-group">
      <label className="col-sm-2 control-label">User</label>
      <div className="col-sm-10">
        <input type="text" className="form-control user" placeholder="Username">
          <Attribute selector="inProgress" attribute="disabled" predicate={isTruthy} />
        </input>
      </div>
    </div>
    <div className="form-group">
      <label className="col-sm-2 control-label">Password</label>
      <div className="col-sm-10">
        <input type="password" className="form-control password" placeholder="Password">
          <Attribute selector="inProgress" attribute="disabled" predicate={isTruthy} />
        </input>
      </div>
    </div>
    <div className="form-group">
      <Match>
        <Case predicate={isError}>
          <div className="col-sm-offset-2 col-sm-10 text-danger">
            Could not login. Check username / password.
          </div>
        </Case>
      </Match>
    </div>
    <div className="form-group">
      <div className="col-sm-offset-2 col-sm-10">
        <button type="submit" className="btn btn-primary">
          <Attribute selector="inProgress" attribute="disabled" predicate={isTruthy} />
          <Match>
            <Case predicate={isLoading}>
              <span className="glyphicon glyphicon-refresh spin"></span>
            </Case>
            <Default>
              Login
            </Default>
          </Match>
        </button>
      </div>
    </div>
  </form>;

  return (parentNode, eventsBus) => {
    const update = template(parentNode, eventsBus);
    const passwordField = parentNode.querySelector('.password');

    return [
      update,
      ({error}) => {
        if (error) {
          passwordField.focus();
          passwordField.select();
        }
      }
    ];
  };

  function isLoading({inProgress}) {
    return inProgress;
  }

  function isError({error}) {
    return error;
  }

  function submit({event}) {
    event.preventDefault();

    const user = event.target.querySelector('.user').value;
    const password = event.target.querySelector('.password').value;

    performLogin(user, password);
  }
}
