import asyncio
import base64
import logging
import os
import shlex
import sys
import time
from asyncio.subprocess import PIPE
from dataclasses import dataclass
from typing import Self

import asyncclick as click
import httpx
import yaml
from rich.console import Console

from .auth import AuthContext
from .cmd import run
from .utils import TaskGroup

console = Console()

logger = logging.getLogger("load-test-tool")


@dataclass
class Cluster:
    namespace: str
    version: str

    async def forward_cluster(self: Self) -> None:
        namespace = self.namespace

        logger.info(f"Port-forwarding for the cluster in {namespace}")
        async with asyncio.TaskGroup() as tg:
            keycloak = port_forward(namespace, "keycloak", 18080, 18080)

            logger.info("Creating port-forward for Keycloak")
            tg.create_task(keycloak, name="port-forward")

            if self.version == "8.7":
                logger.info("Creating port-forward for Operate")
                tg.create_task(
                    port_forward(namespace, "operate", 8080, 80), name="operate"
                )
                logger.info("Creating port-forward for Identity")
                tg.create_task(
                    port_forward(namespace, "identity", 8081, 80), name="identity"
                )
            else:
                logger.info("Creating port-forward for Camunda")
                tg.create_task(
                    port_forward(namespace, "camunda-gateway", 8080), name="gateway"
                )

            logger.info("Creating port-forward for Optimize")
            tg.create_task(
                port_forward(namespace, "optimize", 8083, 80), name="optimize"
            )
            logger.info("Creating port-forward for Identity")
            tg.create_task(
                port_forward(namespace, "identity", 8084, 80), name="identity"
            )


# Decorator to pass to commands taking the auth context.
pass_auth = click.make_pass_decorator(AuthContext, ensure=True)


async def port_forward(
    namespace: str, service: str, local_port: int, remote_port: int | None = None
) -> None:
    cmd = [
        "kubectl",
        "--namespace",
        namespace,
        "port-forward",
        f"svc/{service}",
    ]

    if remote_port is not None:
        cmd.append(f"{local_port}:{remote_port}")
    else:
        cmd.append(f"{local_port}")

    while True:
        logger.info("Running command: %s", shlex.join(cmd))
        process = await asyncio.create_subprocess_exec(*cmd, stdout=PIPE, stderr=PIPE)
        res = await process.wait()
        print(f"Port-forward process exited with code {res}")

        if process.returncode is not None:
            stderr = (
                (await process.stderr.read()).decode()
                if process.stderr
                else "Unknown error"
            )
            logger.warning(
                f"Failed to start port-forward for {service} in namespace {namespace} (will retry): {stderr}"
            )
            await asyncio.sleep(1)
            # raise RuntimeError(
            #     f"Failed to start port-forward for {service} in namespace {namespace}: {stderr}"
            # )


async def get_client_secret(ctx: AuthContext, secret_name: str, secret_key: str) -> str:
    jsonpath_query = f".data.{secret_key}"
    cmd = [
        "kubectl",
        "--namespace",
        ctx.namespace,
        "get",
        "secret",
        secret_name,
        "-o",
        "jsonpath={" + jsonpath_query + "}",
    ]
    logger.debug(
        f"Retrieving client secret from Kubernetes secret {ctx.client_secret_name}..."
    )
    data = await run(*cmd)

    logger.debug("Got secret")

    secret = base64.decodebytes(data.stdout.encode("utf-8"))
    return secret.decode("utf-8")


async def get_token(ctx: AuthContext, client_secret: str) -> str:
    async with httpx.AsyncClient() as client:
        data = {
            "grant_type": "client_credentials",
            "client_id": ctx.client_id,
            "client_secret": client_secret,
            "audience": ctx.audience,
        }

        logger.info(f"Requesting token from {ctx.oauth_url}...")
        response = await client.post(ctx.oauth_url, data=data)
        response.raise_for_status()
        token = response.json()["access_token"]
        return token


async def wait_url(url: str, timeout: int = 60, delay: int = 1) -> None:
    async with httpx.AsyncClient() as client:
        start = time.time()
        while time.time() - start < timeout:
            try:
                await client.get(url)
                break
            except httpx.HTTPError:
                logger.info(f"Waiting for {url} to be available...")
                await asyncio.sleep(delay)
        else:
            raise RuntimeError(f"Timeout waiting for {url} to be available")


async def wait_and_get_token(ctx: AuthContext, client_secret: str) -> str:
    await wait_url(ctx.oauth_url)
    token = await get_token(ctx, client_secret)
    return token


@click.group()
@click.option("-n", "--namespace", help="Kubernetes namespace", default=None)
@click.option("--base-url", default="http://localhost:8080", help="Camunda base URL")
@click.option(
    "--oauth-url",
    default="http://localhost:18080/auth/realms/camunda-platform/protocol/openid-connect/token",
    help="Camunda OAuth URL",
)
@click.option("--client-id", default="orchestration", help="Camunda client ID")
@click.option(
    "--client-secret-name",
    default="camunda-credentials",
    help="Kubernetes secret name for Camunda client secret",
)
@click.option(
    "--client-secret-key",
    default="orchestration-security-authentication-oidc-secret",
    help="Key in the Kubernetes secret for Camunda client secret",
)
@click.option("--audience", default="orchestration-api", help="Camunda token audience")
@click.option(
    "-v",
    "--verbose",
    count=True,
    help="Increase log level",
)
@click.option(
    "-q",
    "--quiet",
    count=True,
    help="Decrease log level",
)
@click.pass_context
async def main(
    ctx: click.Context,
    namespace: str | None,
    base_url: str,
    oauth_url: str,
    client_id: str,
    client_secret_name: str,
    client_secret_key: str,
    audience: str,
    quiet: int,
    verbose: int,
):
    cli_context = await AuthContext.new(
        namespace=namespace,
        base_url=base_url,
        oauth_url=oauth_url,
        client_id=client_id,
        client_secret_name=client_secret_name,
        client_secret_key=client_secret_key,
        audience=audience,
    )

    ctx.obj = cli_context
    log_level = logging.WARNING + verbose * -10 + quiet * 10

    logging.basicConfig(
        level=log_level, format="%(levelname)s: %(message)s", stream=sys.stderr
    )


@main.group()
def auth():
    pass


@auth.command("get-token")
@pass_auth
async def cmd_get_token(ctx: AuthContext) -> None:
    namespace = ctx.namespace
    client_secret = await get_client_secret(
        ctx, ctx.client_secret_name, ctx.client_secret_key
    )

    local_port = 18080

    logger.info(f"Starting port-forward for Keycloak {namespace}...")

    async with TaskGroup() as tg:
        tg.add("port-forward", port_forward(namespace, "keycloak", local_port))
        t = tg.add("get-token", wait_and_get_token(ctx, client_secret))

    token = t.result()
    print(token)


async def run_cmd_all(urls, env, cmd):
    async with asyncio.TaskGroup() as tg:
        for url in urls:
            tg.create_task(wait_url(url))

    proc = await asyncio.create_subprocess_exec(*cmd, env=env)
    await proc.wait()


@main.command("run")
@click.argument("cmd", nargs=-1)
@click.option("--use-camunda-gateway", is_flag=True, default=True)
@pass_auth
async def async_cmd_run(ctx: AuthContext, cmd: list[str], use_camunda_gateway: bool):
    cluster = Cluster(namespace=ctx.namespace, version="latest")
    client_secret = await get_client_secret(
        ctx, ctx.client_secret_name, ctx.client_secret_key
    )

    new_env = {
        "CAMUNDA_BASE_URL": ctx.base_url,
        "CAMUNDA_OAUTH_URL": ctx.oauth_url,
        "CAMUNDA_CLIENT_ID": ctx.client_id,
        "CAMUNDA_CLIENT_SECRET": client_secret,
        "CAMUNDA_TOKEN_AUDIENCE": ctx.audience,
    }

    env = {}
    env.update(os.environ)
    env.update(new_env)

    in_subshell = False
    if len(cmd) == 0:
        in_subshell = True
        shell = os.environ.get("SHELL", "/bin/bash")
        style = "black on yellow"
        console.print(
            f"No command provided to run, will spawn a sub-shell instead using: {shell}",
            style=style,
        )
        console.print("Leave the shell with C^d or by entering: exit", style=style)
        console.rule("Entering subshell")
        ps1 = os.environ.get("PS1", "$ ")
        env["PS1"] = f"[load-test-tool] {ps1}"
        cmd = [shell]

    async with TaskGroup() as tg:
        tg.add("port-forward", cluster.forward_cluster())
        tg.add("run-cmd", run_cmd_all([ctx.base_url, ctx.oauth_url], env, cmd))

    if in_subshell:
        console.rule("Leaving sub-shell")


@dataclass
class User:
    username: str
    password: str
    roles: list[str]


async def get_admin_user(ctx: AuthContext) -> User:
    identity_cm = "identity-configuration"

    cmd = [
        "kubectl",
        "--namespace",
        ctx.namespace,
        "get",
        "configmap",
        identity_cm,
        "-o",
        r"jsonpath={.data.application\.yaml}",
    ]
    logger.debug(
        f"Retrieving client secret from Kubernetes secret {ctx.client_secret_name}..."
    )
    data = await run(*cmd)

    config = yaml.safe_load(data.stdout)

    # TODO: this assumes a single user
    if config is None:
        raise RuntimeError(f"Unable to get config map from {shlex.join(cmd)}")

    username = config["keycloak"]["users"][0]["username"]
    roles = config["keycloak"]["users"][0]["roles"]

    password = await get_client_secret(
        ctx, "camunda-credentials", "identity-firstuser-password"
    )

    user = User(username=username, password=password, roles=roles)

    return user

async def get_keycloak_admin_user(ctx: AuthContext) -> User:
    secret = "keycloak-admin-user"

    cmd = [
        "kubectl",
        "--namespace",
        ctx.namespace,
        "get",
        "secret",
        secret,
        "-o",
        r"jsonpath={.data}",
    ]
    logger.debug(
        f"Retrieving client secret from Kubernetes secret {ctx.client_secret_name}..."
    )
    data = await run(*cmd)

    config = yaml.safe_load(data.stdout)

    # TODO: this assumes a single user
    if config is None:
        raise RuntimeError(f"Unable to get config map from {shlex.join(cmd)}")

    username = base64.decodebytes(config["username"].encode("utf-8")).decode("utf-8")
    password = base64.decodebytes(config["password"].encode("utf-8")).decode("utf-8")

    user = User(username=username, password=password, roles=[])

    return user


@main.command("tunnel")
@click.option("-V", "--cluster-version", default="latest")
@pass_auth
async def async_cmd_tunnel(
    ctx: AuthContext,
    cluster_version: str,
) -> None:

    cluster = Cluster(namespace=ctx.namespace, version=cluster_version)

    print = console.print

    user = await get_admin_user(ctx)
    keycloak_user = await get_keycloak_admin_user(ctx)

    async def wait_for() -> None:
        await wait_url(ctx.oauth_url)

        print()
        print("## Credentials")
        print()
        print(f"  - Username: [bold]{user.username}[/bold]")
        print(f"  - Password: [bold]{user.password}[/bold]")
        print()
        print(f"Roles: {', '.join(r for r in sorted(user.roles))}")
        print()
        print("## Services")
        print()
        print("* Open Operate:             http://localhost:8080/operate")
        print("* Open Identity:            http://localhost:8080/admin")
        print("* Open Optimize:            http://localhost:8083")
        print("* Open Management Identity: http://localhost:8084")

        print()
        print("## Keycloak")
        print()
        print(f"  - Admin Username: [bold]{keycloak_user.username}[/bold]")
        print(f"  - Admin Password: [bold]{keycloak_user.password}[/bold]")
        print()
        print("* Open Keycloak:            http://localhost:18080")
        print()
        print("Close the tunnel with C-c")
        await asyncio.sleep(3600 * 24 * 365)  # sleep for a year, until interrupted

    print("Opening up tunnels...")
    async with TaskGroup() as tg:
        tg.add("port-forward", cluster.forward_cluster())
        tg.add("wait", wait_for())


if __name__ == "__main__":
    main()

# vim:ft=python
