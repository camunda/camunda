#!/usr/bin/env python3
"""Silence a monorepo CI alert in Grafana for the length of a fix, and say so in Slack.

Medics hold the Viewer role in Grafana and cannot create silences themselves, so this script
does it as a robot account and enforces the rules that come with that privilege: Unified CI can
never be silenced, a silence expires within a week, and it names the incident it belongs to.
It also creates an audit trail in Slack.
Invoked by `.github/workflows/ci-silence-incident.yml`.

Matchers are `grafana_folder`, `alertname` and `workflow_job` — the alert's notification group
key, so the silence covers every future firing for that job. `grafana_folder` is pinned to the
monorepo CI folder, which keeps the token from touching infrastructure alerts.

A silence suppresses *delivery* only: the rule keeps evaluating and the CI health dashboards
keep recording every failure.

Required environment variables:
  GRAFANA_URL         Base URL of the Grafana API endpoint, e.g. https://api-dashboard.int.camunda.com.
  GRAFANA_USER        LDAP uid of the robot account holding the Editor role in Grafana.
  GRAFANA_PASSWORD    LDAP password for that account.
  SLACK_BOT_TOKEN     Bot token for posting the FYI message.
  SLACK_CHANNEL_ID    Channel to post the FYI into.
  WORKFLOW_JOB        Value of the alert's workflow_job label, copied from the alert.
  INCIDENT            Incident this silence belongs to, e.g. INC-8362.
  ACTOR               GitHub login of the requesting medic.
  RUN_URL             URL of the workflow run, for the audit trail.
  ALERTNAME           Alert rule to silence.
  DAYS                Duration in days, at most 7.

Optional environment variables:
  DRY_RUN             "true" exercises Vault, LDAP, Grafana and Slack without writing anything.
"""

import base64
import json
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

MAX_DAYS = 7
GRAFANA_FOLDER = "General Alerting/monorepo-ci"
INCIDENT_PATTERN = re.compile(r"^INC-\d+$")

# Unified CI gates every merge, so muting it hides breakage for the whole repository rather than
# for one team's job. A broken Unified CI job gets fixed or disabled, never silenced.
FORBIDDEN_JOB_PREFIXES = ("CI: ",)


def fail(message):
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def env(name, default=None):
    value = os.environ.get(name, default)
    # Strip first: a whitespace-only value would otherwise pass as present and then become an
    # empty matcher, silencing far more than the caller asked for.
    value = value.strip() if value else value
    if not value:
        fail(f"{name} is required")
    return value


def request(url, credentials, method="GET", body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Basic {credentials}")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.loads(response.read() or "null")
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")[:500]
        if error.code == 401:
            # Only the LDAP middleware challenges; a 401 without one came from Grafana itself,
            # which means the middleware let the request through but Grafana did not accept the
            # identity it was handed.
            if "Basic" in error.headers.get("WWW-Authenticate", ""):
                fail(
                    f"The LDAP middleware rejected the account. Check NEXUS_USR/NEXUS_PSW under "
                    f"secret/data/github.com/organizations/camunda, and that the account is "
                    f"matched by the middleware's search filter. Response: {detail}"
                )
            fail(
                f"Grafana rejected the identity the LDAP middleware passed it. Check that the "
                f"X-Vouch-User header reaches it and that auth proxy is enabled. Response: {detail}"
            )
        if error.code == 403:
            fail(
                f"Grafana knows the account but will not let it write silences — it is most "
                f"likely still on the default Viewer role and needs Editor. Response: {detail}"
            )
        fail(f"{method} {url} failed with HTTP {error.code}: {detail}")
    except urllib.error.URLError as error:
        fail(f"{method} {url} is unreachable: {error.reason}")


def matchers(alertname, workflow_job):
    return [
        {"name": "grafana_folder", "value": GRAFANA_FOLDER, "isRegex": False, "isEqual": True},
        {"name": "alertname", "value": alertname, "isRegex": False, "isEqual": True},
        {"name": "workflow_job", "value": workflow_job, "isRegex": False, "isEqual": True},
    ]


def existing_silence(base_url, credentials, wanted):
    wanted_set = {(m["name"], m["value"]) for m in wanted}
    for silence in request(f"{base_url}/api/alertmanager/grafana/api/v2/silences", credentials) or []:
        if silence.get("status", {}).get("state") != "active":
            continue
        current = {(m["name"], m["value"]) for m in silence.get("matchers", [])}
        if current == wanted_set:
            return silence
    return None


def check_slack_token(token):
    """Prove the bot token still works without posting anything."""
    req = urllib.request.Request("https://slack.com/api/auth.test", data=b"", method="POST")
    req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            result = json.loads(response.read() or "{}")
    except (urllib.error.HTTPError, urllib.error.URLError) as error:
        fail(f"Slack is unreachable: {error}")
    if not result.get("ok"):
        fail(f"The Slack bot token was refused: {result.get('error', 'unknown error')}")
    return result.get("user", "unknown")


def dry_run(base_url, credentials, slack_token):
    """Walk every dependency the real run has, stopping short of the silence itself.

    The failure this guards against is silent: credentials rotate, an LDAP account is
    disabled, a Grafana role is reset, and nobody finds out until a medic needs the tool
    during an incident.
    """
    user = request(f"{base_url}/api/user", credentials)
    orgs = request(f"{base_url}/api/user/orgs", credentials) or []
    roles = [org.get("role") for org in orgs]
    if not any(role in ("Editor", "Admin") for role in roles):
        fail(
            f"{user.get('login')} authenticates but holds {roles or 'no role'} in Grafana. "
            f"Creating a silence needs Editor, so a real run would fail with a 403."
        )
    # Read-only, and it is what the real run calls before writing.
    request(f"{base_url}/api/alertmanager/grafana/api/v2/silences", credentials)
    slack_user = check_slack_token(slack_token)
    print(
        f"Dry run OK — Grafana user `{user.get('login')}` holds {roles}, the silence API answers, "
        f"and the Slack bot authenticates as `{slack_user}`. No silence was created."
    )


def post_to_slack(token, channel, text):
    body = json.dumps(
        {
            "channel": channel,
            "text": text,
            "blocks": [{"type": "section", "text": {"type": "mrkdwn", "text": text}}],
        }
    )
    req = urllib.request.Request("https://slack.com/api/chat.postMessage", data=body.encode(), method="POST")
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Content-Type", "application/json; charset=utf-8")
    # The silence exists by now, so both messages say so: the run fails to flag that the
    # announcement is missing, not to suggest that nothing happened.
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            result = json.loads(response.read() or "{}")
    except (urllib.error.HTTPError, urllib.error.URLError) as error:
        fail(f"The silence was created but Slack is unreachable, so announce it by hand: {error}")
    # chat.postMessage answers 200 even when it refuses, e.g. not_in_channel when the bot has
    # not been invited to the channel.
    if not result.get("ok"):
        fail(
            f"The silence was created but Slack refused the notice, so announce it by hand: "
            f"{result.get('error', 'unknown error')}"
        )


def main():
    base_url = env("GRAFANA_URL").rstrip("/")
    credentials = base64.b64encode(
        f"{env('GRAFANA_USER')}:{env('GRAFANA_PASSWORD')}".encode()
    ).decode()
    slack_token = env("SLACK_BOT_TOKEN")
    slack_channel = env("SLACK_CHANNEL_ID")
    workflow_job = env("WORKFLOW_JOB")
    incident = env("INCIDENT").upper()
    actor = env("ACTOR")
    run_url = env("RUN_URL")
    alertname = env("ALERTNAME")

    if not INCIDENT_PATTERN.match(incident):
        fail(f"INCIDENT must look like INC-1234, got '{incident}'")

    if workflow_job.startswith(FORBIDDEN_JOB_PREFIXES):
        fail(
            f"'{workflow_job}' is a Unified CI job. Unified CI gates every merge, so silencing it "
            f"hides breakage for the whole repository — fix the job, or disable it explicitly in "
            f"its workflow file so the change is visible in review."
        )

    raw_days = env("DAYS")
    try:
        days = int(raw_days)
    except ValueError:
        fail(f"DAYS must be a whole number of days, got '{raw_days}'")
    if not 1 <= days <= MAX_DAYS:
        fail(f"DAYS must be between 1 and {MAX_DAYS}, got {days}")

    if os.environ.get("DRY_RUN", "").lower() == "true":
        dry_run(base_url, credentials, slack_token)
        return

    starts_at = datetime.now(timezone.utc)
    ends_at = starts_at + timedelta(days=days)
    wanted = matchers(alertname, workflow_job)

    already = existing_silence(base_url, credentials, wanted)
    if already:
        fail(
            f"An active silence for this job already exists (id {already.get('id')}, expires "
            f"{already.get('endsAt')}). The job is already covered; if you need a different "
            f"window, ask the Monorepo CI medic to expire it."
        )

    created = request(
        f"{base_url}/api/alertmanager/grafana/api/v2/silences",
        credentials,
        method="POST",
        body={
            "matchers": wanted,
            "startsAt": starts_at.isoformat().replace("+00:00", "Z"),
            "endsAt": ends_at.isoformat().replace("+00:00", "Z"),
            "createdBy": actor,
            "comment": f"{incident}: silenced by @{actor} while the fix is in progress ({run_url})",
        },
    )
    silence_id = (created or {}).get("silenceID", "unknown")
    silence_url = f"{base_url}/alerting/silence/{silence_id}/edit"
    until = ends_at.strftime("%Y-%m-%d %H:%M UTC")

    print(
        f"Silenced `{workflow_job}` ({alertname}) until {until} for {incident} — silence {silence_id}"
    )
    post_to_slack(
        slack_token,
        slack_channel,
        f":no_bell: *CI alert `{alertname}` silenced* for `{workflow_job}`\n"
        f"• Until *{until}* ({days}d), for <https://app.incident.io/camunda/incidents/"
        f"{incident.removeprefix('INC-')}|{incident}>\n"
        f"• Requested by *{actor}* via <{run_url}|this workflow run>\n"
        f"• It expires on its own; ending it sooner is a Grafana write, which medics cannot do "
        f"— ask the Monorepo CI medic. Silence: <{silence_url}|{silence_id}>",
    )


if __name__ == "__main__":
    main()
