# Authentication

# Introduction
Operate provides 2 ways for authentication:

1. Authenticate with user information stored in elasticsearch
2. Authenticate with a Single-Sign-On provider 

Default enabled is user storage in elasticsearch. 

# User in elasticsearch

In this mode the user authenticates with username and password. 
**username** and **password** can be set in application.yml:

```
camunda.operate:
  username: anUser
  password: aPassword
```
If the user doesn't exist it will be created. 

## Default values

Default **username** is `demo` and **password** is `demo`.


# Single-Sign-On

## Enable Single-Sign-On

Single-Sign-On is only enabled by setting [spring profile](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-profiles): `sso-auth`

Example for setting spring profiles as environment variable:
```
export SPRING_PROFILES_ACTIVE=sso-auth
```

## Configure Single-Sign-On

Single-Sign-On needs following parameters:

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
