<!DOCTYPE html>
<html lang="en">
<head>
    <title>Login – ${realm.displayName?default(realm.name)}</title>
    <link rel="stylesheet" href="css/realm-custom.css"/>
    <style>
        .login-header {
            text-align: center;
            margin-bottom: 2em;
        }
        .realm-banner {
            margin: 1em auto;
            background: #e7f5ff;
            border-left: 5px solid #2b7cf6;
            padding: 1em 2em;
            max-width: 420px;
            font-size: 1.1em;
        }
        .realm-logo {
            width: 120px;
            margin-bottom: 1em;
        }
        body {
            font-family: system-ui, sans-serif;
        }
    </style>
</head>
<body>
    <div class="login-header">
        <h1>Welcome to ${realm.displayName?default(realm.name)}</h1>
    </div>

    <div class="realm-banner">
        <strong>Notice:</strong> You are signing in to the <span style="color:#2b7cf6; font-weight:bold;">${realm.name}</span> realm.
    </div>

    <form id="kc-form-login" action="${url.loginAction}" method="post">
        <!-- Insert Keycloak's standard login fields here, or use the default template blocks -->
        <label for="username">Username</label><br>
        <input id="username" name="username" type="text" autofocus><br><br>

        <label for="password">Password</label><br>
        <input id="password" name="password" type="password"><br><br>

        <button type="submit">Sign In</button>
    </form>
</body>
</html>
