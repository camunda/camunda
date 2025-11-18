# Orchestration Cluster Docker Compose

This is a repository of reusable, extensible Docker Compose definitions and related application
configuration options. The goal is to allow contributors to quickly spin up local development
environments based on pre-defined set ups, but in a way that allows mixing certain core features in
a composable way.

## Usage

The simplest way to get started is just to run

```sh
docker compose up -d
```

This will start a single node OC, using ES as your DB, and basic authentication.

To compose your own OC instances with existing presets, you basically start with the `oc.yml` file,
and then add any concerns you require. You typically need to specify at least a DB, and you may
want to specify an authentication concern.

> [!Note]
> By convention, Docker Compose files for concerns are prefixed with clear identifiers. For example,
> DB/secondary storage set ups will be `db.es.yml`, or `db.os.yml`. Authentication concerns will be
> `auth.basic.yml`, or `auth.oidc.yml`.

For example, if you want to run the same OC but with OIDC, you would launch:

```sh
docker compose -f oc.yml -f db.es.yml -f auth.oidc.yml up -d
```

> [!Note]
> Remember that to bring everything down, you need to run the _same_ command as you did to bring
> them up, but swap `up -d` for `down -v`.

## Conventions

### File names

Files relevant to specific concerns should be prefixed with a clear identifier. Use:

- `db` as a prefix for files configuring the secondary storage
- `auth` as a prefix for files configuration authentication and security related things

### Locations

Docker Compose files remain in the top level `docker` folder, and configuration files are stored in
the `config` sub-folder.

### Image versions

To simplify things, keep image versions in the `.env` file. Docker Compose will automatically load
this and make available, and it makes it easy to dynamically swap the versions via the CLI.

### Fixed ports

We use fixed ports in general to simplify usage, as otherwise it makes it very hard to wire things
like Keycloak and OC for the authorization flows if you don't know the ports in advance.

## Extending

The whole point of this set up is that we can extend it, and then mix and match concerns to quickly
set up whatever local environment you need. So keep this in mind when extending it yourself, and if
in doubt, don't hesitate to ask someone who's already done this.

### The Orchestration Cluster (OC) Service

It starts with the Orchestration Cluster service definition, which you find in [oc.yml](./oc.yml).
This is a single cluster node definition, with largely default configuration options. It's set up
with a volume so it can be stopped and restarted without losing data, and also set up to load
additional configuration. Loading additional configuration is done via normal Spring mechanisms.

Spring will load any `application.yml` it finds under the path
`/usr/local/camunda/config/additional`, so we mount our additional configuration files in their own
directory in there, grouped by concerns. For example, configuration for the DB is always mounted as
`/usr/local/camunda/config/additional/db/application.yml`, or for the authentication configuration,
under `/usr/local/camunda/config/additional/auth/application.yml`.

Grouping them by concern allows us to make sure the "last" included Docker Compose file will "win",
and overwrite the previous configuration file for this concern.

Once running, the OC is accessible using the default ports: 8080, 26500, and 9600.

### OIDC

You can quickly set up OC with an OIDC provider by using the `auth.oidc.yml` concern:

```sh
docker compose up -f oc.yml -f db.es.yml -f auth.oidc.yml up -d
```

This will set up Keycloak with a prepared realm (see [config/realm.json](./config/realm.json)), with
a single user (username: `demo`, password: `demo`) who is an admin.

You can then access Keycloak via `http://localhost:18080` (user: `admin`, password: `admin`).

Keycloak is set up with an embedded H2 DB and its own volume to keep its data between restarts.

### ES

You can set up OC with an ES secondary storage by using the `db.es.yml` concern:

```sh
docker compose up -f oc.yml -f db.es.yml up -d
```

This will set up a local single node ES cluster, accessible via port 9200. It has its own volume
so it will keep its data across restarts.

