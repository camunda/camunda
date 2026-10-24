from dataclasses import dataclass
from typing import Self

from .cmd import run


@dataclass
class AuthContext:
    namespace: str
    base_url: str
    oauth_url: str
    client_id: str
    client_secret_name: str
    client_secret_key: str
    audience: str

    @classmethod
    async def new(
        cls,
        *,
        namespace: str | None,
        base_url: str,
        oauth_url: str,
        client_id: str,
        client_secret_name: str,
        client_secret_key: str,
        audience: str,
    ) -> Self:
        if namespace is None:
            namespace = (await run("kubens", "-c")).stdout.strip()

        return cls(
            namespace=namespace,
            base_url=base_url,
            oauth_url=oauth_url,
            client_id=client_id,
            client_secret_name=client_secret_name,
            client_secret_key=client_secret_key,
            audience=audience,
        )
