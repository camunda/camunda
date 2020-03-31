# Authentication

# Introduction

Operate provides two ways for authentication:

1. Authenticate with user information stored in [Elasticsearch](#user-in-elasticsearch)
2. Authenticate via [Auth0 Single Sign-On provider](#auth0-single-sign-on)  

By default user storage in Elasticsearch is enabled. 

# User in Elasticsearch

In this mode the user authenticates with username and password, that are stored in Elasticsearch. 
**username** and **password** for one user may be set in application.yml:

```
camunda.operate:
  username: anUser
  password: aPassword
```

On Operate startup the user will be created if not existed before. 

By default one user with **username**/**password** `demo`/`demo` will be created.

More users can be added directly to Elasticsearch, to the index `operate-user_`. Password must be encoded with BCrypt strong hashing function.

# Auth0 Single Sign-On

Currently Operate supports Auth0.com implementation of Single Sign-On.

## Enable Single Sign-On

Single Sign-On may be enabled only by setting [Spring profile](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-profiles): `sso-auth`

Example for setting spring profile as environmental variable:
```
export SPRING_PROFILES_ACTIVE=sso-auth
```

## Configure Single Sign-On

Single Sign-On needs following parameters:

Parametername | Optional | Default value | Description
--------------|----------|---------------|-------------
camunda.operate.auth0.domain | yes | login.cloud.ultrawombat.com |  Defines the domain which the user sees 
camunda.operate.auth0.backendDomain | yes | camunda-dev.eu.auth0.com |  Defines the domain which provides user information
camunda.operate.auth0.clientId | no | | It's like an user name for the application
camunda.operate.auth0.clientSecret | no | | It's like a password for the application
camunda.operate.auth0.claimName | yes | https://camunda.com/orgs |The claim that will be checked by Operate. It's like a permission name
camunda.operate.auth0.organization | no | | The given organization should be contained in value of claim name

Example for setting parameters as environment variables:

```
export CAMUNDA_OPERATE_AUTH0_CLIENTID=A_CLIENT_ID
export CAMUNDA_OPERATE_AUTH0_CLIENTSECRET=A_SECRET
export CAMUNDA_OPERATE_AUTH0_ORGANIZATION=AN_ORGANIZATION
```
