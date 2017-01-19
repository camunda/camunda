import {jsx, SetInputFieldValue, OnEvent, Match, Case, Default, withSelector, Attribute, isTruthy} from 'view-utils';
import {performLogin, changePassword, changeUser} from './loginForm.service';

export const LoginForm = withSelector(Form);

function Form() {
  return <form className="form-horizontal form-signin">
    <OnEvent event="submit" listener={submit} />
    <h2>Login</h2>
    <div className="form-group">
      <label className="col-sm-2 control-label">User</label>
      <div className="col-sm-10">
        <input type="text" className="form-control" placeholder="Username">
          <Attribute selector="inProgress" attribute="disabled" predicate={isTruthy} />
          <SetInputFieldValue getValue="user" />
          <OnEvent event={['keyup', 'change']} listener={onUserKeyup} />
        </input>
      </div>
    </div>
    <div className="form-group">
      <label className="col-sm-2 control-label">Password</label>
      <div className="col-sm-10">
        <input type="password" className="form-control" placeholder="Password">
          <Attribute selector="inProgress" attribute="disabled" predicate={isTruthy} />
          <SetInputFieldValue getValue="password" />
          <OnEvent event={['keyup', 'change']} listener={onPasswordKeyup} />
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

  function isLoading({inProgress}) {
    return inProgress;
  }

  function isError({error}) {
    return error;
  }

  function submit({state: {user, password}, event}) {
    event.preventDefault();

    performLogin(user, password);
  }

  function onUserKeyup({node}) {
    changeUser(node.value);
  }

  function onPasswordKeyup({node}) {
    changePassword(node.value);
  }
}
