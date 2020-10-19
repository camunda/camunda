# Authentication

# Introduction

Operate provides three ways for authentication:

1. Authenticate with user information stored in [Elasticsearch](#user-in-elasticsearch)
2. Authenticate via [Auth0 Single Sign-On provider](#auth0-single-sign-on)
3. Authenticate via [Lightweight Directory Access Protocol (LDAP)](#ldap)

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

More users can be added directly to Elasticsearch, to the index `operate-user-<version>_`. Password must be encoded with BCrypt strong hashing function.

# Auth0 Single Sign-On

Currently Operate supports Auth0.com implementation of Single Sign-On.

## Enable Single Sign-On

Single Sign-On may be enabled only by setting [Spring profile](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-profiles): `sso-auth`

Example for setting spring profile as environmental variable:
```
export SPRING_PROFILES_ACTIVE=sso-auth
```

## Configure Single Sign-On

Single Sign-On needs following parameters (all are mandatory):

Parametername |Description
--------------|-------------
camunda.operate.auth0.domain | Defines the domain which the user sees
camunda.operate.auth0.backendDomain | Defines the domain which provides user information
camunda.operate.auth0.clientId | It's like an user name for the application
camunda.operate.auth0.clientSecret | It's like a password for the application
camunda.operate.auth0.claimName | The claim that will be checked by Operate. It's like a permission name
camunda.operate.auth0.organization | The given organization should be contained in value of claim name

Example for setting parameters as environment variables:

```
export CAMUNDA_OPERATE_AUTH0_DOMAIN=A_DOMAIN
export CAMUNDA_OPERATE_AUTH0_BACKENDDOMAIN=A_BACKEND_DDOMAIN
export CAMUNDA_OPERATE_AUTH0_CLIENTID=A_CLIENT_ID
export CAMUNDA_OPERATE_AUTH0_CLIENTSECRET=A_SECRET
export CAMUNDA_OPERATE_AUTH0_CLAIMNAME=A_CLAIM
export CAMUNDA_OPERATE_AUTH0_ORGANIZATION=AN_ORGANIZATION
```
# LDAP

## Enable LDAP

LDAP can be enabled only by setting [Spring profile](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-profiles): `ldap-auth`

Example for setting spring profile as environmental variable:
```
export SPRING_PROFILES_ACTIVE=ldap-auth
```

## Configuration of LDAP
A user can authenticate via LDAP.
Following parameters for a connection to a LDAP server should be given:

 Parametername |Description | Example| Required
 --------------|------------|---------|--------
 camunda.operate.ldap.url | URL to a LDAP Server | ldaps://camunda.com/ | yes
 camunda.operate.ldap.baseDn| Base domain name | dc=camunda,dc=com| yes
 camunda.operate.ldap.managerDn| Manager domain, is used by Operate to login into LDAP Server to retrieve user informations | cn=admin,dc=camunda,dc=com| yes
 camunda.operate.ldap.managerPassword| Password for manager| |yes
 camunda.operate.ldap.userSearchFilter| Filter to retrieve user info, The pattern '{0}' will be replaced by given username in login form| {0} | no, Default is {0}
 camunda.operate.ldap.userSearchBase| Starting point for search| ou=Support,dc=camunda,dc=com| no

## Configuration of Active Directory based LDAP
For **Active Directory** based LDAP server following parameters should  be given:

Note: Only when `camunda.operate.ldap.domain` is given, the Active Directory configuration will be applied.

 Parametername |Description |  Required
 --------------|------------|---------
 camunda.operate.ldap.url | URL to a Active Directory LDAP Server |  yes
 camunda.operate.ldap.domain| Domain | yes
 camunda.operate.ldap.baseDn| Root domain name | no
 camunda.operate.ldap.userSearchFilter| Is used as search filter | no

